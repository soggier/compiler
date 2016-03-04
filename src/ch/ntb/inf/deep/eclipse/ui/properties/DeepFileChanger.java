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

package ch.ntb.inf.deep.eclipse.ui.properties;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import ch.ntb.inf.deep.eclipse.DeepPlugin;
import ch.ntb.inf.deep.eclipse.ui.preferences.PreferenceConstants;

public class DeepFileChanger {
	StringBuffer fileContent;
	String deepFile;
	
	public DeepFileChanger(String name) {
		deepFile = name;
		try {
			fileContent = new StringBuffer();
			BufferedReader reader = new BufferedReader(new FileReader(deepFile));
			
			int ch  = reader.read();
			while(ch  != -1) {
				fileContent.append((char)ch);
				ch = reader.read();
			}
			reader.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public String getContent(String key) {
		int start = 0;
		int indexOfKey = fileContent.indexOf(key, start);
		while (indexOfKey > -1) {
			int indexOfComment = fileContent.lastIndexOf("#", indexOfKey);
			int indexOfNewLine = fileContent.lastIndexOf("\n", indexOfKey);
			if (indexOfComment < indexOfNewLine) {
				int indexOfStartToken = fileContent.indexOf("=", indexOfKey);
				int indexOfEndToken = fileContent.indexOf(";", indexOfKey);
				if (indexOfStartToken < 0 || indexOfEndToken < 0) return null;
				String str = fileContent.substring(indexOfStartToken+1, indexOfEndToken);
				return str.trim();	
			} else { // its a comment
				start = indexOfKey + 1;
				indexOfKey = fileContent.indexOf(key, start);
			}
		}
		return null;
	}

	public void setContent(String key, String value) {
		int start = 0;
		int indexOfKey = fileContent.indexOf(key, start);
		while (indexOfKey > -1) {
			int indexOfComment = fileContent.lastIndexOf("#", indexOfKey);
			int indexOfNewLine = fileContent.lastIndexOf("\n", indexOfKey);
			int indexOfEndToken = fileContent.indexOf(";", indexOfKey);
			if (indexOfComment < indexOfNewLine) {
				fileContent.replace(indexOfKey, indexOfEndToken, key + " = " + value);
				return;
			} else { // its a comment
				start = indexOfKey + 1;
				indexOfKey = fileContent.indexOf(key, start);
			}
		}
		//not found, append new line
		addContent(key, value);
	}

	public void removeContent(String key) {
		int start = 0;
		int indexOfKey = fileContent.indexOf(key, start);
		while (indexOfKey > -1) {
			int indexOfComment = fileContent.lastIndexOf("#", indexOfKey);
			int indexOfNewLine = fileContent.lastIndexOf("\n", indexOfKey);
			int indexOfEndToken = fileContent.indexOf(";", indexOfKey);
			if (indexOfComment < indexOfNewLine) {
				fileContent.replace(indexOfKey, indexOfEndToken + 1, "");
				return;
			} else { // its a comment
				start = indexOfKey + 1;
				indexOfKey = fileContent.indexOf(key, start);
			}
		}
	}
	
	public void changeProjectName(String newName) {
		int indexOfBracket = fileContent.indexOf("{", fileContent.indexOf("{", 0) + 1);
		int indexOfNewLine = (fileContent.substring(0, indexOfBracket)).lastIndexOf("\n", indexOfBracket);
		fileContent.replace(indexOfNewLine + 1, indexOfBracket, "project " + newName + " ");
	}
	
	public void changeLibPath(String newPath) {
		if(newPath == null)
			newPath = DeepPlugin.getDefault().getPreferenceStore().getString(PreferenceConstants.DEFAULT_LIBRARY_PATH);
		
		String key = "<classpathentry kind=\"lib\" path=\"";
		int indexOfKey = fileContent.indexOf(key);
		if (indexOfKey > -1){
			int indexOfEndtoken = fileContent.indexOf("/>", indexOfKey);
			File srcFolder = new File(newPath + "/src");
			if (srcFolder.exists()){
				fileContent.replace(indexOfKey, indexOfEndtoken, key + newPath + "/bin\" sourcepath=\"" + newPath + "/src\"");
			} else {				
				fileContent.replace(indexOfKey, indexOfEndtoken, key + newPath + "/bin\"");
			}
		}
	}

	public void addContent(String key, String value){
		int start = 0;
		int lastOccurenceOfKey = -1;
		int indexOfKey = -1; 
		
		while(fileContent.indexOf(key, start) != -1){
			indexOfKey = fileContent.indexOf(key, start);
			start = indexOfKey + 1;
		}
		lastOccurenceOfKey = indexOfKey;
		if(lastOccurenceOfKey == -1){					//key doesn't exist -> add at end of file
			int indexOfEnd = fileContent.lastIndexOf("}");
			fileContent.insert(indexOfEnd - 1, "\n\t" + key + " = " + value + ";");
		}
		else{
			int indexOfNewLine = fileContent.indexOf("\n", lastOccurenceOfKey);
			fileContent.insert(indexOfNewLine, "\n\t" + key + " = " + value + ";");
		}
	}
	
	public void commentContent(String key) {
		int start = 0;
		int indexOfKey = 0;
		int indexOfNewLine = 0;
		int indexOfLastNewLine = 0;
		
		while(fileContent.indexOf(key, start) != -1){
			indexOfKey = fileContent.indexOf(key, start);
			while(indexOfNewLine < indexOfKey){
				indexOfLastNewLine = indexOfNewLine;
				indexOfNewLine = fileContent.indexOf("\n", indexOfNewLine + 1);
			}
			fileContent.replace(indexOfLastNewLine + 1, indexOfLastNewLine + 2, "#");
			if(!fileContent.substring(indexOfLastNewLine + 1, indexOfLastNewLine + 3).equalsIgnoreCase("#\t")){
				fileContent.insert(indexOfLastNewLine + 2, "\t");
			}
			start = indexOfKey + 1;
		}
	}
	
	public void save() {
		try {
			BufferedWriter out = new BufferedWriter(new FileWriter(deepFile));
			out.write(fileContent.toString());
			out.flush();
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

