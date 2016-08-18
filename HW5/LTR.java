/** 
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */
import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

import org.apache.lucene.index.Term;

import java.util.LinkedHashMap;
import java.util.HashMap;

//import org.apache.lucene.queryparser.flexible.core.util.StringUtils;

/* 
This file adds four new capabilities to the search engine that you developed for previous homework assignments. Your program must:

Read training queries and relevance judgments from input files;
Calculate feature vectors for training documents;
Write the feature vectors to a file;
Call SVMrank to train a retrieval model;
Read test queries from an input file;
Use BM25 to get inital rankings for test queries;
Calculate feature vectors for the top 100 ranked documents (for each query);
Write the feature vectors to a file;
Call SVMrank to calculate scores for test documents;
Read the scores produced by SVMrank; and
Write the final ranking in trec_eval input format.
Your program must implement the following features.

f1: Spam score for d (read from index). 
Hint: The spam score is stored in your index as the score attribute. (We know that this is a terrible name. Sorry.) 
int spamScore = Integer.parseInt (Idx.getAttribute ("score", docid));
f2: Url depth for d(number of '/' in the rawUrl field). 
Hint: The raw URL is stored in your index as the rawUrl attribute. 
String rawUrl = Idx.getAttribute ("rawUrl", docid);
f3: FromWikipedia score for d (1 if the rawUrl contains "wikipedia.org", otherwise 0).
f4: PageRank score for d (read from file).
f5: BM25 score for <q, dbody>.
f6: Indri score for <q, dbody>.
f7: Term overlap score for <q, dbody>. 
Hint: Term overlap is defined as the percentage of query terms that match the document field.
f8: BM25 score for <q, dtitle>.
f9: Indri score for <q, dtitle>.
f10: Term overlap score for <q, dtitle>.
f11: BM25 score for <q, durl>.
f12: Indri score for <q, durl>.
f13: Term overlap score for <q, durl>.
f14: BM25 score for <q, dinlink>.
f15: Indri score for <q, dinlink>.
f16: Term overlap score for <q, dinlink>.
f17: A custom feature - use your imagination.
f18: A custom feature - use your imagination.
*/

/* The core function which is doing all the jobs by calling respective functions */
public class LTR{

	static int numFeatures = 18; // Maximum number of features
	Map<String,String > LTRFileParameters; // All the input parameters
	static int Error = 1234567;
	int numFeaturesEnabled=0,err_count=0;

	// BM25 and Indri parameters
	double BM25_k_1,BM25_b,BM25_k_3;
	double Indri_mu,Indri_lambda;
	
	// Map for disabled features : 0 means disabled, 1 means enabled
	Map<Integer,Integer> FeatureEnabled = new HashMap<Integer,Integer>();
	
	//Query Map : Stored the query Id(string) and the corresponding tokens[] of queries(stemmed and stop word removed)
	LinkedHashMap<String,String[]> queryMap = new LinkedHashMap<String,String[]>();
	ArrayList<Document> documentList = new ArrayList<Document>();
	Map<String,String> PageRankMap = new LinkedHashMap<String,String>();
	
	// Related the query with the min and max value of the features (for normalization)
	Map<String,ArrayList<Double>> q_min_values  = new LinkedHashMap<String,ArrayList<Double>>();
	Map<String,ArrayList<Double>> q_max_values  = new LinkedHashMap<String,ArrayList<Double>>();
	
	// Main function which will trigger LETOR module, get the input and write the output
	public void MainLTR(Map<String,String> parameters){
		
		// Copy the parameters from the parent func. to local variable
		LTRFileParameters = parameters;
		
		CopyModelParams();
			
		// Create a Map for the Query Id and the Tokenized terms
		FillQueryMap();
	    	
	    // Create an Map for the PageRank
	    FillPageRank();

	    // Add all the documents to the structure 
	    AddDocuments();
			
	    // Normalizing the weights
		NormalizeWeights();
			
		// Writing the results for SVM Training
		WriteResultsSVM(LTRFileParameters.get("letor:trainingFeatureVectorsFile"));
		
		// SVM Training : Learning
		SVMTraining();
			
		// Clear the data structure associated with training, to be used for BM25
		clearAllTraining();
			
		// Run BM25 to extract top docs on test queries 
		BM25Results();
			
		// SVM Classifier on BM25 Results
		SVMClassifier();
		
		// Re-rank the documents based
		ReRankDocument();
		
		//Write the final result to the output file 
		WriteResults();
	}
	
	// Copy BM25 and Indri parameters to the class variable 
	public void CopyModelParams(){
	
		BM25_k_1 = Double.parseDouble(LTRFileParameters.get("BM25:k_1"));
		BM25_b = Double.parseDouble(LTRFileParameters.get("BM25:b"));
		BM25_k_3 = Double.parseDouble(LTRFileParameters.get("BM25:k_3"));
		Indri_mu = Double.parseDouble(LTRFileParameters.get("Indri:mu"));
		Indri_lambda = Double.parseDouble(LTRFileParameters.get("Indri:lambda"));
		
		//System.out.println("BM25 params : k1 = " + BM25_k_1  + " b = " + BM25_b + " k3 = " + BM25_k_3);
		//System.out.println("Indri : mu = " +Indri_mu + " lambda = " +  Indri_lambda);
		
		// First fill the map will all the feature enabled by default
		for(int feat_idx=1;feat_idx<=numFeatures;feat_idx++){
			FeatureEnabled.put(feat_idx,1);
		}
		numFeaturesEnabled= numFeatures;
		
		// Extract the features which are disabled.
		String feature_disabled = LTRFileParameters.get("letor:featureDisable");
		if(feature_disabled==null){
			System.out.println("No feature is disabled...");
			return;
		}
		System.out.println(" feature disabled string = " + feature_disabled);
		
		String[] tokens = feature_disabled.split(",");
		
		for(int token_idx=0;token_idx<tokens.length;token_idx++){
			
			System.out.println("token = " + tokens[token_idx]);
			FeatureEnabled.put(Integer.parseInt(tokens[token_idx]), 0);
		}
		
		System.out.println("Feature values after updating disabled features");
		for(int feat_idx=1;feat_idx<=numFeatures;feat_idx++){
			System.out.print( feat_idx + ":" + FeatureEnabled.get(feat_idx)  + "  ");
		}
		
		numFeaturesEnabled -= tokens.length;
	}
	
	// Check if the given feature is disabled or not 
	public void IsFeatureDisabled(int feature_num){
		
		if((feature_num<=0) || (feature_num > numFeatures)){
			System.out.println(" incorrect feature, feature requested = " + feature_num);
			return;
		}
		
	}
	
	// Fill the map of query and corresponding tokenised terms
	public void FillQueryMap(){
		
		String[] qryTerms = null;
		String qLine = null,qid,query;
		BufferedReader trainingInput =null;
		
		try {
	    	
	    	String trainingQueryFilePath = LTRFileParameters.get("letor:trainingQueryFile");
	    	trainingInput = new BufferedReader(new FileReader(trainingQueryFilePath));
	    	
	    	while ((qLine = trainingInput.readLine()) != null) {
	    		
	    		int d = qLine.indexOf(':');
	            
	    		qid = qLine.substring(0, d);
	    		query = qLine.substring(d + 1);
	    		qryTerms= QryEval.tokenizeQuery(query);
	    		if(qryTerms==null){
	    			System.out.println(" All qry terms stopped, assigning null to qryId = " + qid);
	    		}
	    		queryMap.put(qid, qryTerms);
	    	} 
	    	
	    	trainingInput.close();
		}catch (IOException e) {
			e.printStackTrace();
		}finally{
			// Do nothing
		}
		
	}
	
	// Fill the page rank into an HashMap, which would be used for direct mapping of the
	public void FillPageRank(){
		
		BufferedReader pageRank=null;
		try{
			String  pageRankFilePath= LTRFileParameters.get("letor:pageRankFile");
			pageRank = new BufferedReader(new FileReader(pageRankFilePath));
			String pageRankLine = "";
			String[] PageRankTokens;
		
			while((pageRankLine = pageRank.readLine()) != null){
				PageRankTokens = pageRankLine.split("\\s+");
				PageRankMap.put(PageRankTokens[0].trim(), PageRankTokens[1].trim());	
			}
			pageRank.close();
			
		}catch(Exception e){
			e.printStackTrace();
		}finally{
			// Do Nothing
		}
		
	}
	
	// Add all the documents to a main structure 
	public void AddDocuments(){
		
		// Structure that contains all the documents, its feature vector, related query and other things
		ArrayList<ArrayList<Double>> tempFeatureValue = new ArrayList<ArrayList<Double>>();
		
		String trainingResultsFilePath = LTRFileParameters.get("letor:trainingQrelsFile");
		BufferedReader trainingResult = null;
		String docLine = null,prevQryID = null;
		int count=0;
	
		System.out.println("Adding Documents ...");
		
		try{
			
			trainingResult = new BufferedReader(new FileReader(trainingResultsFilePath));
			
			while((docLine = trainingResult.readLine()) != null){
		
      		//Extract the doc Id(External) and the evaluation score of the doc.
			String[] docTokens = docLine.split("\\s+");
			
			// Create a new doc, gather all the values etc. and add it to documentList
			Document new_doc = new Document();
			
      		//Extract the feature vector for this document and the corresponding query.
			new_doc.qid = docTokens[0].trim();
			new_doc.externalDocID = docTokens[2].trim();
			new_doc.judgment = docTokens[3].trim();
      		
      		try{
      			new_doc.internalDocID = Idx.getInternalDocid(new_doc.externalDocID);
      		}catch(Exception e){
      			new_doc.internalDocID = Error;
      			continue;
      		}
      		
      		new_doc.featuresValues = getFeatureValues(new_doc.qid,new_doc);	
      		
      		documentList.add(new_doc);
      		
      		if((prevQryID!=null) && (prevQryID.equals(new_doc.qid)==false)){
      			getFeaturesExtremePerQuery(prevQryID,tempFeatureValue);
      			count++;
      			tempFeatureValue.clear();
      		} 
      		
      		tempFeatureValue.add(new_doc.featuresValues);
      		prevQryID = new_doc.qid;
		}
	
		// For the last query 
		getFeaturesExtremePerQuery(prevQryID,tempFeatureValue);
		count++;
		//System.out.println(" QryId = " + prevQryID + " num docs = " + tempFeatureValue.size() + " count = " + 
			//	count);
  
		}catch(Exception e){
			e.printStackTrace();
		}	
		
	}
	
	// Update min and max values per feature for a given query and set of corresponding docs
	public void getFeaturesExtremePerQuery(String QryID,ArrayList<ArrayList<Double>> tempFeatureValue){
		
		/* Go through each feature and get the min and max value */
		ArrayList<Double> temp_array = new ArrayList<Double>();
		ArrayList<Double> minFeatureValue = new ArrayList<Double>();
		ArrayList<Double> maxFeatureValue = new ArrayList<Double>();
		
		//System.out.println(" QryID = " + QryID + " Feature Vec size = " + tempFeatureValue.size());
			
		//System.out.println("Min and Max feature values extraction");
		for(int feature_idx=0;feature_idx<numFeaturesEnabled;feature_idx++){
				
			/* Go through all the documents for this query*/
			double min_val = Double.MAX_VALUE, max_val = Double.MIN_VALUE;
			
			for(int doc_idx=0;doc_idx<tempFeatureValue.size();doc_idx++){
					
				temp_array = tempFeatureValue.get(doc_idx);
					
				if(temp_array.get(feature_idx)==(double)Error){
					err_count++;
					continue;
				}
					
				if(temp_array.get(feature_idx) > max_val){
					max_val = temp_array.get(feature_idx);
				}else if(temp_array.get(feature_idx) < min_val){
					min_val = temp_array.get(feature_idx);
				}	
					
			}
			
			//System.out.println(" QrID = " + QryID + " feature id = " + feature_idx +" err_count = " + err_count);
				
			/* Now, we have the minimum and maximum value for this feature */
			if((min_val==(double)Error) || (max_val==(double)Error)){
				System.out.println(" Min/Max val is Error :( ");
			}
			
			//System.out.println(" QrID = " + QryID + " feature id = " + feature_idx + " min = " + min_val + " max = " + max_val);
			minFeatureValue.add(min_val);
			maxFeatureValue.add(max_val);
		}

		/* We have the minimum and maximum value for all the features across all the documents */
		q_min_values.put(QryID, minFeatureValue);
		q_max_values.put(QryID, maxFeatureValue);
	}
	
	/* Normalize the weights of all the documents */
	public void NormalizeWeights(){
		
		int document_size=documentList.size();
		ArrayList<Double> minVal,maxVal;
		double min_feature_val=0.0, max_feature_val=0.0, normalizedValue=0.0;
		String docQryID;
		Document temp_doc;
		int err_count=0;
		
		System.out.println("Weights normalization ...");
		
		try{
		/* Go through each document and normalize all the feature weights */
		for(int doc_idx=0;doc_idx <document_size;doc_idx++){
			
			/* Go through each feature and normalize it */
			temp_doc = documentList.get(doc_idx);
			docQryID = temp_doc.qid;
			
			if(temp_doc.internalDocID==Error){
				System.out.println("Shouldn't happen !!");
				continue;
			}
			
			ArrayList<Double> normalizedFeatureArray = new ArrayList<Double>();
			minVal = q_min_values.get(docQryID);
			maxVal = q_max_values.get(docQryID);
			
			if((minVal==null) || (maxVal==null)){
				System.out.println(" Null min or max for QryID = " + docQryID);
				continue;
			}
			
			//System.out.println(" doc Idx = " + doc_idx + " docQryId = " + docQryID + " min vector size =  " + minVal.size() + 
			//					" max vector size = " + maxVal.size() + " num features  = " + numFeatures);
			
			for(int feature_idx=0;feature_idx<numFeaturesEnabled;feature_idx++){
				
				min_feature_val = minVal.get(feature_idx);
				max_feature_val = maxVal.get(feature_idx); 

				if((temp_doc.featuresValues.get(feature_idx)==(double)Error)){
					err_count++;
				}
				
				if((min_feature_val==max_feature_val) || 
						(temp_doc.featuresValues.get(feature_idx)==(double)Error)){
					normalizedFeatureArray.add((double) 0);
				}else{
					normalizedValue = (double)(temp_doc.featuresValues.get(feature_idx)-min_feature_val)/(double)(max_feature_val-min_feature_val);
					normalizedFeatureArray.add(normalizedValue);	
				}
			}
			
			temp_doc.featuresValues = normalizedFeatureArray;	
		}
		}catch(Exception e){
			e.printStackTrace();
		}
		//System.out.println("Total error count while normalizing  = " + err_count);	
	}
	
	//SVMTraining 
	public void SVMTraining(){
		
		// runs svm_rank_learn from within Java to train the model
	    // execPath is the location of the svm_rank_learn utility, 
	    // which is specified by letor:svmRankLearnPath in the parameter file.
	    // FEAT_GEN.c is the value of the letor:c parameter.
		
		String c = LTRFileParameters.get("letor:svmRankParamC");
		File svmTrainingInputfile = new File(LTRFileParameters.get("letor:svmRankLearnPath"));
		String execPath1 = svmTrainingInputfile.getAbsolutePath();
		String qrelsFeatureOutputFile = LTRFileParameters.get("letor:trainingFeatureVectorsFile");
		String modelOutputFile = LTRFileParameters.get("letor:svmRankModelFile");
				
	    Process cmdProc = null;
		try {
			cmdProc = Runtime.getRuntime().exec(
			new String[] { execPath1, "-c", String.valueOf(c), qrelsFeatureOutputFile,
			        modelOutputFile });
			
	    // consume stdout and print it out for debugging purposes
	    BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(cmdProc.getInputStream()));
	    String line;
	    while ((line = stdoutReader.readLine()) != null) {
	    		//System.out.println(line);
	    }
	    // consume stderr and print it for debugging purposes
	    BufferedReader stderrReader = new BufferedReader(
	        new InputStreamReader(cmdProc.getErrorStream()));
	    while ((line = stderrReader.readLine()) != null) {
	      //System.out.println(line);
	    }

		} catch (IOException e) {
			e.printStackTrace();
		}
	    // get the return value from the executable. 0 means success, non-zero 
	    // indicates a problem
	    int retValue = 0;
		try {
			retValue = cmdProc.waitFor();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	    if (retValue != 0) {
	    	System.out.println("SVM Rank crashed");
	    	//throw new Exception("SVM Rank crashed.");
	    }
	}
	
	//SVMTraining 
	public void SVMClassifier(){
		
		File svmTrainingInputfile = new File(LTRFileParameters.get("letor:svmRankClassifyPath"));
		String execPath = svmTrainingInputfile.getAbsolutePath();
		String qrelsFeatureOutputFile = LTRFileParameters.get("letor:testingFeatureVectorsFile");
		String svmLearnedModel = LTRFileParameters.get("letor:svmRankModelFile");
		String modelOutputFile = LTRFileParameters.get("letor:testingDocumentScores");
		
	    Process cmdProc = null;
		try {
			cmdProc = Runtime.getRuntime().exec(
			new String[] { execPath, qrelsFeatureOutputFile, svmLearnedModel, modelOutputFile }
			);

	    // The stdout/stderr consuming code MUST be included.
	    // It prevents the OS from running out of output buffer space and stalling.

	    // consume stdout and print it out for debugging purposes
	    BufferedReader stdoutReader = new BufferedReader(new InputStreamReader(cmdProc.getInputStream()));
	    String line;
	    while ((line = stdoutReader.readLine()) != null) {
	    		//System.out.println(line);
	    }
	    
	    // consume stderr and print it for debugging purposes
	    BufferedReader stderrReader = new BufferedReader(new InputStreamReader(cmdProc.getErrorStream()));
	    while ((line = stderrReader.readLine()) != null) {
	     // System.out.println(line);
	    }

		} catch (IOException e) {
			e.printStackTrace();
		}
		
	    int retValue = 0;
		try {
			retValue = cmdProc.waitFor();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	    if (retValue != 0) {
	    	System.out.println("SVM Rank crashed");
	    	//throw new Exception("SVM Rank crashed.");
	    }
	}
	
	// Clear all the training related data structures 
	public void clearAllTraining(){
		queryMap.clear();
  		documentList.clear();
  		q_min_values.clear();
  		q_max_values.clear();
	}
	
	// Run BM25 on the test queries and write the result vector 
	public void BM25Results(){
		
		String qLine,qid,query;
		String[] qryTerms;

		try{
			String testQueryFilePath = LTRFileParameters.get("queryFilePath");
			BufferedReader testInput = new BufferedReader(new FileReader(testQueryFilePath));
			RetrievalModel model =new RetrievalModelBM25(BM25_k_1,BM25_b,BM25_k_3);
			
			while ((qLine = testInput.readLine()) != null) {
				int d = qLine.indexOf(':');
				qid = qLine.substring(0, d);
				query = qLine.substring(d + 1);
	    		qryTerms= QryEval.tokenizeQuery(query);
	    		queryMap.put(qid, qryTerms);
			}
			
			testInput.close();
			err_count=0;
			
			BufferedReader testInput2 = new BufferedReader(new FileReader(testQueryFilePath));
			
			while ((qLine = testInput2.readLine()) != null) {
				
				int d = qLine.indexOf(':');
				qid = qLine.substring(0, d);
				query = qLine.substring(d + 1);
				
				ArrayList<Double> Feature = new ArrayList<Double>();
				ArrayList<ArrayList<Double>> tempFeatureValue = new ArrayList<ArrayList<Double>>();
				
				ScoreList r = null;
				r = QryEval.processQuery(query,model);
				
				for(int doc_idx=0;doc_idx< r.size();doc_idx++){
					
					Document new_doc = new Document();
					new_doc.externalDocID = r.getExternalDocid(doc_idx);
					new_doc.judgment = Integer.toString(0);
					new_doc.qid = qid;
					
					try{
						new_doc.internalDocID = Idx.getInternalDocid(new_doc.externalDocID);
					}catch(Exception e){
						new_doc.internalDocID = Error;
						continue;
					}
					
					Feature = getFeatureValues(qid,new_doc);
					new_doc.featuresValues = Feature;
					tempFeatureValue.add(Feature);
					
	          		documentList.add(new_doc);
				}
				getFeaturesExtremePerQuery(qid,tempFeatureValue);
			}
			testInput2.close();
			
		}catch(Exception e){
			e.printStackTrace();
    	}
		
		System.out.println(" Testing : err_cnt = " + err_count);
		NormalizeWeights();
		WriteResultsSVM(LTRFileParameters.get("letor:testingFeatureVectorsFile"));
	}
	
	// Write the results to the file for SVM Training
	public void WriteResultsSVM(String filePath){
		
		File svmTrainingInputfile = new File(filePath);
		String outputString = "";
		Document tempDoc;
		
    	// Create SVM file where training data will be inserted 
		try{
			
			if (!svmTrainingInputfile.exists()) {
				svmTrainingInputfile.createNewFile();
			}

			FileWriter fw = new FileWriter(svmTrainingInputfile.getAbsoluteFile(),true);
			BufferedWriter bw = new BufferedWriter(fw);
		
			// First sort the documents based on the Qry id -> External Document ID 
			Collections.sort(documentList, new DocSortFunction(documentList));
			
			// Write the results to the output file as per the below example
			// Example : 2 qid:1 1:1 2:1 3:0 4:0.2 5:0 # clueweb09-en0000-48-24794
			
			for(int doc_idx=0;doc_idx< documentList.size() ;doc_idx++){
				
				tempDoc = documentList.get(doc_idx);
				if(tempDoc.internalDocID==Error){
					System.out.println("Skipping doc as external doc id not found");
					continue;
				}
				
				outputString = "";
				outputString = outputString + tempDoc.judgment + " qid:" + tempDoc.qid + " " ;
				
				int enable_Feature_index=-1;
				for(int f_idx=1;f_idx<=numFeatures;f_idx++){
					
					if(FeatureEnabled.get(f_idx)==0){
						continue;
					}
					enable_Feature_index++;
					
					outputString  =  outputString + Integer.toString(f_idx) + ":";
					
					if(tempDoc.featuresValues.get(enable_Feature_index)==(double)Error){
						System.out.println("feature still Error after normalization");
						outputString =  outputString + "0.0";
					}else{
						outputString = outputString + Double.toString(tempDoc.featuresValues.get(enable_Feature_index)) + " ";
					}
					
				}
				
				outputString = outputString + " # " + tempDoc.externalDocID;
				//System.out.println(outputString);
				bw.write(outputString);
				bw.newLine();
			}
			
			bw.close();		
		}catch(Exception e){
			e.printStackTrace();
		}
	
	}
	
	// Get all the feature value for the given query(string) and the docID(external ID-String) 
	public ArrayList<Double> getFeatureValues(String qryId,Document doc){
		
		ArrayList<Double> featureVal = new ArrayList<Double>();
		
		//System.out.println("Getting feature for qryID = " + qryId);
		
		String externalDocID = doc.externalDocID;
		
		// Corner scenario, if the doc doesn't exist in the corpus but given in the ranking
		int internalDocId = doc.internalDocID;
		if(internalDocId==Error){
			
			//System.out.println("No external docID, returning featureVector with val = " + (double)Error);
			for(int f_idx=0;f_idx<=numFeatures;f_idx++){
				featureVal.add((double)Error);
			} 
			return featureVal;
		}
		
		try {
			
			if(FeatureEnabled.get(1)==1){
				featureVal.add((double)(Integer.parseInt(Idx.getAttribute ("score", internalDocId))));
			}
			
			String rawUrl =  Idx.getAttribute ("rawUrl", internalDocId);
			if(FeatureEnabled.get(2)==1){
				int depth=0;
				for(int idx=0;idx<rawUrl.length();idx++){
					if(rawUrl.charAt(idx)=='/') {
						depth++;
					}
				}
				featureVal.add((double)depth);
			}
			
			if(FeatureEnabled.get(3)==1){
				int f3 = 0;
				if (rawUrl.indexOf("wikipedia.org") != -1) {
					f3 = 1;
				}
				featureVal.add((double)f3);
			}
			
			if(FeatureEnabled.get(4)==1){
				if(PageRankMap.get(externalDocID)==null){
					//System.out.println("Page Rank is null for docId = " +  doc.externalDocID);
					featureVal.add((double)Error);
				}else{
					//System.out.println("Feature 4: (Page Rank) = " + Double.parseDouble(PageRankMap.get(externalDocID)));
					featureVal.add(Double.parseDouble(PageRankMap.get(externalDocID)));
				}
			}
			
			// Other features: "body", "title", "url", "inlink"
			if(FeatureEnabled.get(5)==1){
				featureVal.add(featureBM25(qryId,internalDocId,"body"));
			}
			if(FeatureEnabled.get(6)==1){
				featureVal.add(featureIndri(qryId,internalDocId,"body"));
			}
			if(FeatureEnabled.get(7)==1){
				featureVal.add(featureTermOverlap(qryId,internalDocId,"body"));
			}
			if(FeatureEnabled.get(8)==1){
				featureVal.add(featureBM25(qryId,internalDocId,"title"));
			}
			if(FeatureEnabled.get(9)==1){
				featureVal.add(featureIndri(qryId,internalDocId,"title"));
			}
			if(FeatureEnabled.get(10)==1){
				featureVal.add(featureTermOverlap(qryId,internalDocId,"title"));
			}
			if(FeatureEnabled.get(11)==1){
				featureVal.add(featureBM25(qryId,internalDocId,"url"));
			}
			if(FeatureEnabled.get(12)==1){
				featureVal.add(featureIndri(qryId,internalDocId,"url"));
			}
			if(FeatureEnabled.get(13)==1){
				featureVal.add(featureTermOverlap(qryId,internalDocId,"url"));
			}
			if(FeatureEnabled.get(14)==1){
				featureVal.add(featureBM25(qryId,internalDocId,"inlink"));
			}
			if(FeatureEnabled.get(15)==1){
				featureVal.add(featureIndri(qryId,internalDocId,"inlink"));
			}
			if(FeatureEnabled.get(16)==1){
				featureVal.add(featureTermOverlap(qryId,internalDocId,"inlink"));
			}
			if(FeatureEnabled.get(17)==1){
				featureVal.add(featureVectorSpace(qryId,internalDocId,"body"));
			}
			if(FeatureEnabled.get(18)==1){
				featureVal.add(feature18(qryId,internalDocId,"body"));
			}
			
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return featureVal;
	}

	
	/* Feature#5,8,11,14
	/* Calculate the score of BM25 for the given query, doc and the field */
	public double featureBM25(String queryId,int docId, String field){
		
		double score = 0;
		TermVector tv;
		String[] words = queryMap.get(queryId);
		
		if(words==null){
			System.out.println("Word len is zero for queryId = " + queryId);
			return (double)Error;
		}
		/* Shouldn't happen */
		if(words.length==0){
			System.out.println("Word len is zero !!!");
			return (double)Error;
		}
		
		//System.out.println("BM25 score eval : QryId = " + queryId + " docId = " + docId + " field = " + field);
		
		try{
			
			tv = new TermVector(docId , field);
			int stem_length = tv.stemsLength();

			if(stem_length==0){
				//System.out.println("BM25 feature : Stem len=0 for field = " + field);
				return (double)Error;
			}
			
			for(int idx=0;idx<words.length;idx++){	
				
				if(tv.indexOfStem(words[idx])==-1){
					continue;
				}		
				
				int stemIdx = tv.indexOfStem(words[idx]);
				double tf = (double)tv.stemFreq(stemIdx),df = (double) tv.stemDf(stemIdx);
				double num_docs = (double)Idx.getDocCount(field);
				double tot_len = (double)Idx.getSumOfFieldLengths(field);
				double doc_length = (double)Idx.getFieldLength(field,docId);
				double average_len = tot_len/num_docs;	
				double idf = Math.max(Math.log(Idx.getNumDocs() - df +0.50) - Math.log(df+ 0.50),0.00);
				double tf_weight = tf/(tf + BM25_k_1*((1-BM25_b) + (BM25_b*doc_length/average_len)));
				
				score = score + idf*tf_weight;
				//System.out.println("QryTerm = " + words[idx] + " stemIdx = " + stemIdx + 
				//	" tf = " + tf + " df = " + df + " num doc = " + num_docs);
				//System.out.println(" tot_len = " + tot_len + "  doc_len = " + doc_length + " avg_len = " + average_len);
				//System.out.println(" idf = " + idf + " tf = " + tf_weight + " score = " + score);
				//System.out.println();
			}
			
		}catch(Exception e){
			System.out.println(" WTF !!");
		}
			
		return score;
	}
	
	/* Feature#6,9,12,15
	/* Calculate the score of BM25 for the given query, doc and the field */
	public double featureIndri(String queryId,int docId, String field){
		
		double score = 1.0;
		TermVector tv;
		String[] words = queryMap.get(queryId);
		
		try{
			
			tv = new TermVector(docId , field);
			double tot_len = (double)Idx.getSumOfFieldLengths(field);
	    	double doc_length = (double)Idx.getFieldLength(field,docId);
	    	
			int stem_length = tv.stemsLength();
			
			if((stem_length==0)){
				return (double)Error;
			}
			
			int idx=0;
			for(idx=0;idx<words.length;idx++){
	    		int stemIdx = tv.indexOfStem(words[idx]);
	    		if(stemIdx!=-1){
	    			break;
	    		}
	    	}
			
			if(idx==words.length){
				return 0.0;
			}
			
	    	for(idx=0;idx<words.length;idx++){
	    		
	    		int stemIdx = tv.indexOfStem(words[idx]);
	    		
	    		if(stemIdx!=-1){
					double tf = (double)tv.stemFreq(stemIdx);
					double ctf = (double)tv.totalStemFreq(stemIdx);
			    	double prior = ctf/tot_len;
				    score *= (1-Indri_lambda)*(tf+Indri_mu*prior)/(doc_length + Indri_mu) + (Indri_lambda)*prior;
					
				}else{
					double ctf = (double)Idx.INDEXREADER.totalTermFreq(new Term(field,words[idx]));
			    	double prior = ctf/tot_len;
				    score *= (1-Indri_lambda)*(Indri_mu*prior)/(doc_length + Indri_mu) + (Indri_lambda)*prior;
				}	
	    		
	    	}
			
		}catch(Exception e){
			e.printStackTrace();
		}
		
		return (Math.pow(score,1/(double)words.length));
	}
	
	// Features#7,10,13,16 : Term overlap score for <q, dbody>. 
	// Hint: Term overlap is defined as the percentage of query terms that match the document field.
	public double featureTermOverlap(String queryId,int docId, String field){
		
		TermVector tv;
		String[] words = queryMap.get(queryId);
		int num_words_match=0;
		
		if(words.length==0){
			System.out.println(" Tokensize words count zero for QryId = " + queryId);
			return 0.00;
		}
		
		try{
			
			tv = new TermVector(docId , field);
			int stem_length = tv.stemsLength();
			
			if((stem_length==0) || (words.length==0)){
				//System.out.println("Stem len is zero");
				return (double)Error;
			}

			for(int idx=0;idx<words.length;idx++){	
				if(tv.indexOfStem(words[idx])!=-1){
					num_words_match++;
				}		
			}
		
		}catch(Exception e){
			e.printStackTrace();
		}
		
		return (double)(num_words_match)/(double)(words.length);
	}

	// Feature#17 : Vector space 
	public double featureVectorSpace(String queryId,int docId, String field){
		
		double score =0.0,preTermScore=0.0,num=0.0, den= 0.0;
		double l=0.0,t=0.0,n=0.0;
		double dln=0.0,qln=0.0,idf=0.0;
		int tf=0, df=0;
		TermVector tv;
		String[] words = queryMap.get(queryId);
		
		try{
			
			tv = new TermVector(docId , field);
			int stemIdx ;
			
			if(tv.stemsLength()==0){
				return 0.0;
			}
			
			for(int qryIdx=0;qryIdx<words.length;qryIdx++){
				
				if(words[qryIdx]==null){
					return 0.0;
				}
				
				stemIdx = tv.indexOfStem(words[qryIdx]);
				
				if(stemIdx==-1){
					continue;
				}
				
				tf = tv.stemFreq(stemIdx);
				df = tv.stemDf(stemIdx);
				
				l = Math.log((double)(tf+1));
				n = (double)Idx.getSumOfFieldLengths(field);
				idf  = Math.log((double)(n+1)/df);
				
				dln += l*l;
				qln += idf*idf;
				num += l*idf;
			}
			
		}catch(Exception e){
			e.printStackTrace();
		}
		
		dln = Math.sqrt(dln);
		qln = Math.sqrt(qln);
		
		if((dln==0) || (qln==0)){
			return 0.0;
		}
		
		score = (double)num/(dln*qln);
		//System.out.println("VectorSpace : DocId " + docId + " VS score= " + score);
		return score;
		
	}
	
	public double feature18(String queryId,int docId, String field){
		
		try {
			TermVector tv = new TermVector(docId , field);
			if(tv.stemsLength()==0){
				return 0.0;
			}
			
			return (double)tv.stemsLength()/(double)tv.positionsLength();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return 0.0;
	}
	// Re Rank the documents based on the scores provided by the SVM classifier and 
	// Write the results into the output file in the trec Eval format.
	public void ReRankDocument(){
	
		//System.out.println("Re Ranking documents !");
		
		try{
			String DocScores = LTRFileParameters.get("letor:testingDocumentScores");
			BufferedReader DocScoreReader = new BufferedReader(new FileReader(DocScores));
			String Line=null,score=null;
			int doc_idx=0;
			
			// Go through the Score List of the BM25 docs and tag it with the associated doc.
			while((Line = DocScoreReader.readLine()) != null){
				
				score = Line.substring(0, Line.length());
				documentList.get(doc_idx).score_str = score;
				documentList.get(doc_idx).score = Double.parseDouble(score);
				doc_idx++;
			}
			
			DocScoreReader.close();
			
		}catch(Exception e){
			e.printStackTrace();
		}
		
	}
	
	public void WriteResults(){
		
		int doc_idx=0;
		
		try{
		//System.out.println("Writing the result to output file");
		
		//Sort the documents based on the score (first thing is qry)
		Collections.sort(documentList, new DocSortFunction(documentList));
	
		// Write the result in the Eval format to the output file 
	    File file = new File(LTRFileParameters.get("trecEvalOutputPath"));

		if (!file.exists()) {
			file.createNewFile();
		}

		FileWriter fw = new FileWriter(file.getAbsoluteFile(),true);
		BufferedWriter bw = new BufferedWriter(fw);
		Document doc = null;
		String QryResult = null;
		
		for(doc_idx=0;doc_idx<documentList.size();doc_idx++){
			
			doc = documentList.get(doc_idx);
			QryResult = doc.qid + " Q0 " +  doc.externalDocID + " " + (new Integer(doc_idx+1)).toString()
					+ " " + doc.score_str + " run-1 ";
		
			bw.write(QryResult);
			bw.newLine();
			QryResult = "";
		}
		
		bw.close();
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
	
	
}