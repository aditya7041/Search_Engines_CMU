/**
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */
import java.io.*;
import java.util.*;

/**
 *  The SYN operator for all retrieval models.
 */
/**
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;

/**
 *  The SUM operator for BM25.
 */
public class QrySopSum extends QrySop {

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
	  
    if (r instanceof RetrievalModelBM25) {
    	return this.getScoreBM25(r);
    }else{
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the OR operator.");
    }
  }
  
  /**
   *  getScore for the BM25 retrieval model.
   *  This function will check the current doc Idx of all the term present in 
   *  argument and then take the maximum score among all.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  private double getScoreBM25(RetrievalModel r) throws IOException {
	  
	    if (! this.docIteratorHasMatchCache()) {
	    	return 0.0;
	    } else {
	
	    	int MinDocID = this.docIteratorGetMatch();
	    	double DocScore = 0.0;
	    	int size = this.args.size();
	    	
	    	for(int i=0;i<size;i++){

	    		QrySop q_i = (QrySop) this.args.get(i);

	    		if(q_i.docIteratorHasMatch(r)){
	    			if(q_i.docIteratorGetMatch()==MinDocID){
	    				DocScore = DocScore + q_i.getScore(r);
	    			}
	    		}
	    	}
	    	return (DocScore);
	    }
	  }

@Override
public double getScoreDefault(RetrievalModel r, int docid) throws IOException {
	// TODO Auto-generated method stub
	return 0;
}

}
