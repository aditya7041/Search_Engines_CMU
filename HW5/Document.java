/** 
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */
import java.io.*;
import java.util.*;

public class Document{
	
	// Query related things of the documents
	public String qid;
	
	// Internal and External Id of the document
	public String externalDocID; // External docID
	public int internalDocID; // Internal docID
	
	// Rank of the document for the given qryId 
	public int rank;
	public int position; // combined position among all the results(irrespective of query)
	
	// Judgement of the document
	public String judgment;
	// Score after SVM classifier
	public double score;
	public String score_str;
	
	// Features related array for this document
	ArrayList<Double> featuresValues;
	
	// Construct to allocat memory to the variables 
	public Document(){
		featuresValues = new ArrayList<Double>();
		score =0.0;
	}
	
}
