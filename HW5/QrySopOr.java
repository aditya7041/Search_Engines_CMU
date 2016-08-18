/**
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  The OR operator for all retrieval models.
 */
public class QrySopOr extends QrySop {

  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
    return this.docIteratorHasMatchMin (r);
  }

  /**
   *  Get a score for the document that docIteratorHasMatch matched.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScore (RetrievalModel r) throws IOException {
	  
    if (r instanceof RetrievalModelUnrankedBoolean) {
      return this.getScoreUnrankedBoolean (r);
    } else if(r instanceof RetrievalModelRankedBoolean){
    	return this.getScoreRankedBoolean(r);
    }else{
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the OR operator.");
    }
  }
  
  /**
   *  getScore for the UnrankedBoolean retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  private double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      return 1.0;
    }
  }
  
  /**
   *  getScore for the RankedBoolean retrieval model.
   *  This function will check the current doc Idx of all the term present in 
   *  argument and then take the maximum score among all.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  private double getScoreRankedBoolean (RetrievalModel r) throws IOException {
	  
	    if (! this.docIteratorHasMatchCache()) {
	    	return 0.0;
	    } else {
	
	    	int MinDocID = this.docIteratorGetMatch();
	    	double MaxTermFreq = Double.MIN_VALUE;
	    	int size = this.args.size();
	    	
	    	for(int i=0;i<size;i++){
	    		
	    		// Go through all the term(score) covered by this OR operator
	    		QrySop q_i = (QrySop) this.args.get(i);
	    		
	    		// Check if the current doc locator is valid or exhausted 
	    		if(q_i.docIteratorHasMatch(r)){

	    			// If the Doc ID matches with the lowest doc(cached), then check
	    			// for the score i.e the term frequency of the word.
	    			if(q_i.docIteratorGetMatch()==MinDocID){

	    				// Update the maximum score i.e. term occurance in cached docID
	    				if(q_i.getScore(r) > MaxTermFreq){
	    					MaxTermFreq = q_i.getScore(r) ;
	    				}
	    				
	    			}
	    		}
	    	}
	    	return (MaxTermFreq);
	    }
	  }

@Override
public double getScoreDefault(RetrievalModel r, int docid) throws IOException {
	// TODO Auto-generated method stub
	return 0;
}

}
