/*
 * Copyright 2011 - 2013 NTB University of Applied Sciences in Technology
 * Buchs, Switzerland, http://www.ntb.ch/inf
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 *   
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package ch.ntb.inf.deep.cg;

import ch.ntb.inf.deep.cfg.CFGNode;
import ch.ntb.inf.deep.classItems.ICclassFileConsts;
import ch.ntb.inf.deep.host.StdStreams;
import ch.ntb.inf.deep.ssa.SSA;
import ch.ntb.inf.deep.ssa.SSAInstructionMnemonics;
import ch.ntb.inf.deep.ssa.SSAInstructionOpcs;
import ch.ntb.inf.deep.ssa.SSANode;
import ch.ntb.inf.deep.ssa.SSAValue;
import ch.ntb.inf.deep.ssa.SSAValueType;
import ch.ntb.inf.deep.ssa.instruction.PhiFunction;
import ch.ntb.inf.deep.ssa.instruction.SSAInstruction;

public class RegAllocator implements SSAInstructionOpcs, SSAValueType, SSAInstructionMnemonics, ICclassFileConsts {
	protected static final boolean dbg = false;

	private static final int nofSSAInstr = 256;
	public static final int maxNofJoins = 32;
	
	protected static SSA ssa;
	protected static int maxStackSlots;
	
	// local and linear copy of all SSA-instructions of all nodes
	protected static SSAInstruction[] instrs = new SSAInstruction[nofSSAInstr];	
	protected static int nofInstructions;
	
	// used for resolving phi functions
	public static SSAValue[] joins = new SSAValue[maxNofJoins], rootJoins = new SSAValue[maxNofJoins];

	protected static void findLastNodeOfPhi(SSA ssa) {
		if (dbg) StdStreams.vrb.println("determine end of range for phi functions in loop headers");
		SSANode b = (SSANode) ssa.cfg.rootNode;
		int last;
		while (b != null) {
			last = 0;
			if (b.isLoopHeader()) {
				// set variable last to last instruction of this node
				SSAValue val = b.instructions[b.nofInstr - 1].result;
				if (val != null) last = val.n;
				// check if predecessor is further down the code and has set last to a higher value
				CFGNode[] pred = b.predecessors;
				for (int i = 0; i < b.nofPredecessors; i++) {
					SSANode n = (SSANode)pred[i];
					if (n.nofInstr == 0) continue; // node could have no ssa instruction (in catch clause)
					val = n.instructions[n.nofInstr - 1].result;
					if (val != null) {
						int end = val.n;
						if (end > last) last = end;
					}
				}
				for (int i = 0; i < b.nofPhiFunc; i++) {
					PhiFunction phi = b.phiFunctions[i];
					phi.last = last;
					SSAInstruction instr = ((SSANode) b.next).instructions[0];
					SSAInstruction instr1 = instr.next;
					instr.next = phi;
					phi.next = instr1;
				}
			}
			b = (SSANode) b.next;
		}			
	}

	protected static void markUsedPhiFunctions() {
		// determine which deleted phi-functions are still used further down
		if (dbg) StdStreams.vrb.println("determine which phi functions are used further down");
		for (int i = 0; i < nofInstructions; i++) {
			SSAInstruction instr = instrs[i];
			SSAValue[] opds = instr.getOperands();
			if (opds != null) {
				for (SSAValue opd : opds) {
					SSAInstruction opdInstr = opd.owner;
					if ((opdInstr != null) && (opdInstr.ssaOpcode == sCPhiFunc)) {
						PhiFunction phi = (PhiFunction)opdInstr;
						if (phi.deleted && instr.ssaOpcode == sCPhiFunc && ((PhiFunction)instr).deleted) continue;
						if (phi.deleted && phi != instr) {
//							if (dbg) StdStreams.vrb.println("\tphi-function is deleted");
							phi.used = true;
							SSAValue delPhiOpd = phi.getOperands()[0];
							opdInstr = delPhiOpd.owner;
							if (opdInstr.ssaOpcode == sCPhiFunc) {	
								phi = (PhiFunction)opdInstr;
								if (phi.deleted) {
									phi.used = true;
								}
							}
							// set "used" in all del phi-functions in dominators
							SSANode origin = getNode(opdInstr);
							SSANode current = getNode(phi);
							markInDominators(origin, current, phi.result.index);
						}
					}
				}	
			} 
		}
	}

	protected static SSANode getNode(SSAInstruction opdInstr) {
		SSANode b = (SSANode) ssa.cfg.rootNode;
		boolean found = false;
		while (b != null) {
			for (int i = 0; i < b.nofPhiFunc; i++) {
				if (b.phiFunctions[i] == opdInstr) found = true;
			}
			for (int i = 0; i < b.nofInstr; i++) {
				if (b.instructions[i] == opdInstr) found = true;
			}
			if (found) break;
			b = (SSANode) b.next;
		}	
		if (found) return b; else return null;
	}

	protected static void resolvePhiFunctions() {
		if (dbg) StdStreams.vrb.println("resolving phi functions, set joins");
		// first run
		for (int i = 0; i < nofInstructions; i++) {
			SSAInstruction instr = instrs[i];
			//			instr.print(2);
			if (instr.ssaOpcode == sCPhiFunc) {
				//			StdStreams.vrb.print("processing: "); instr.print(0);
				PhiFunction phi = (PhiFunction)instr;
				if (phi.deleted && !phi.used) continue;
				SSAValue[] opds = instr.getOperands();
				SSAValue res = phi.result;
				for (SSAValue opd : opds) {	// set range of phi functions
					if (opd.owner.ssaOpcode == sCloadLocal) res.start = 0;
					if (res.start > opd.n) res.start = opd.n;
					if (res.start > res.n) res.start = res.n;
					if (res.end < opd.n) res.end = opd.n;
					if (res.end < res.n) res.end = res.n;
				}

				SSAValue joinVal;
				joinVal = joins[res.index];
				while (joinVal != null && joinVal.next != null) joinVal = joinVal.next;
				if (joinVal == null) {
					joinVal = joins[res.index] = new SSAValue();
					joinVal.start = res.start;
					joinVal.end = res.end;
					joinVal.type = res.type;
				} else {
					//				StdStreams.vrb.println("\tjoin val exists for index: " + res.index + " res: " + res.start + " to " + res.end + " joinVal: " + joinVal.start + " to " + joinVal.end);
					if (res.start <= joinVal.end) { // does range overlap with current join?
						//					StdStreams.vrb.println("\t\tjoinVal overlaps");
						// it could even overlap with previous joins
						SSAValue join = joins[res.index]; 
						while (join.next != null) {
							if (res.start <= join.end) { // does range overlap with previous join, then merge
								//							StdStreams.vrb.println("\t\tmerge joins: prevJoin:" + join.start + " to " + join.end);
								res.join = join;
								SSAValue ptr = join.next;
								while (ptr != null) {
									for (int k = 0; k < nofInstructions; k++) {
										if (instrs[k].ssaOpcode == sCPhiFunc && instrs[k].result.join == ptr) 
											instrs[k].result.join = join;
									}						
									ptr = ptr.next;
								}
								join.next = null;
								joinVal = join;
							}
							if (join.next != null) join = join.next;
						}
						if (res.start < joinVal.start) joinVal.start = res.start;
						if (res.end > joinVal.end) joinVal.end = res.end;
					} else {	// does not overlap, create new join
						//					StdStreams.vrb.println("joinVal does not overlap, create new join value");
						joinVal.next = new SSAValue();
						joinVal = joinVal.next;
						joinVal.start = res.start;
						joinVal.end = res.end;
						joinVal.type = res.type;
					}
				}

				assert joinVal != null;
				res.join = joinVal;	// set join of phi function
				res.join.index = res.index;
			} 				
		}
		
		// 2nd run, set joins of all operands of phi functions
		for (int i = 0; i < nofInstructions; i++) {
			SSAInstruction instr = instrs[i];
			if (instr.ssaOpcode == sCPhiFunc) {
				PhiFunction phi = (PhiFunction)instr;
				if ((phi.deleted && !phi.used)) continue;
				SSAValue[] opds = instr.getOperands();
				for (SSAValue opd : opds) {
					if (opd.owner.ssaOpcode == sCPhiFunc) continue;	// already set
					if (phi.result.join != null) {
						opd.join = phi.result.join;
					}
				}
			} 		
		}
	}

	protected static void markInDominators(SSANode origin, SSANode current, int index) {
		SSANode node = current;
		while (node.idom != null && node != origin && node.idom != origin) {
			node = (SSANode)node.idom;
			SSAValue val = node.exitSet[index];
			if (val != null) {
				SSAInstruction instr = node.exitSet[index].owner;
				if (instr.ssaOpcode == sCPhiFunc) {
					PhiFunction phi = (PhiFunction)instr;
					if (phi.deleted) phi.used = true;
				}
			}
		}
	}

	// computes the live ranges of all SSAValues 
	protected static void calcLiveRange(SSA ssa) {
		if (dbg) StdStreams.vrb.println("calculating live ranges");
		for (int i = 0; i < nofInstructions; i++) {
			SSAInstruction instr = instrs[i];
			SSAValue res = instr.result;
			int currNo = res.n;
			res.end = currNo;	// set end to current instruction
			SSAValue[] opds = instr.getOperands();
			
			if (instr.ssaOpcode == sCPhiFunc) {	
				if (res.join == null) continue;	// phi is deleted and not used
				int last = ((PhiFunction)instr).last;
				if (res.join.end < last) res.join.end = last;
			}
			
			if (res.join != null) {	// set start of join
				if (res.join.start > currNo) {
//					StdStreams.vrb.println("start was: " + res.join.start);
					res.join.start = currNo;
//					StdStreams.vrb.println("start set to: " + currNo);
				}
			}
			
			if (opds != null) {
				for (SSAValue opd : opds) {
//					if (dbg) StdStreams.vrb.println("\t\topd : " + opd.n);
					SSAInstruction opdInstr = opd.owner;
					if (opd.join == null) {	// regular operand
						if (opd.end < currNo) opd.end = currNo; 
					} else {	// is operand of phi function 
						if (opd.join.end < currNo) opd.join.end = currNo;
						if (opd.join.start > currNo) opd.join.start = currNo;
					}
					if ((opdInstr != null) && (opdInstr.ssaOpcode == sCloadLocal)) {	
						if (opd.join != null) opd.join.start = 0;
						// store last use of a parameter
						CodeGen.paramRegEnd[opdInstr.result.index - maxStackSlots] = currNo;
					}
				}
			}
		}
	}

	public static String joinsToString() {
		StringBuilder sb = new StringBuilder();
		sb.append("joins at index: [");
		for (int i = 0; i < 32; i++) {
			if (joins[i] != null) sb.append("x");
			sb.append(",");
		}
		sb.append("]\nlive ranges of phi functions\n");
		for (int i = 0; i < 32; i++) {
			if (joins[i] != null) {
				sb.append("\tindex=" + joins[i].index);
				SSAValue next = joins[i];
				while (next != null) {
					sb.append(": start=" + next.start);
					sb.append(", end=" + next.end);
					if (next.nonVol) sb.append(", nonVol"); else sb.append(", vol");
					sb.append(", type=" + next.typeName());
					if (next.typeName() == "Long") sb.append(", regLong=" + next.regLong);
					sb.append(", reg=" + next.reg);
					if (next.regGPR1 > -1) sb.append(", regAux1=" + next.regGPR1);
					next = next.next;
				}
				sb.append("\n");
			}
		}
		return sb.toString();
	}

}