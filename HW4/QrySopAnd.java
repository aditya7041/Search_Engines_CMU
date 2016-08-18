import java.io.*;

/**
 *  The AND operator for all retrieval models.
 */
public class QrySopAnd extends QrySop {

  /**
   *  Indicates whether the query has a match i.e all terms should be 
   *  present in a particular doc. This is taken care by 
   *  docIteratorHasMatchMax(model).
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */

  public boolean docIteratorHasMatch (RetrievalModel r) {
    
	  if(r instanceof RetrievalModelIndri){
		  return this.docIteratorHasMatchMin (r);
	  }else{
		  return this.docIteratorHasMatchMax (r);    	
    }
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
    } else if (r instanceof RetrievalModelRankedBoolean){
      return this.getScoreRankedBoolean (r);
    }else if (r instanceof RetrievalModelIndri){
        return this.getScoreIndri (r);
      }else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the AND operator.");
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
      return 1.0; // For unranked, score of all the doc is 1 only by default.
    }
  }

  /**
   *  getScore for the RankedBoolean retrieval model for AND query.
   *  Go through all the terms covered by the and look for the cached docID.
   *  Now, get the minimum term frequency of the doc since this is an AND
   *  operator.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  private double getScoreRankedBoolean (RetrievalModel r) throws IOException {
	  
	    if (! this.docIteratorHasMatchCache()) {
	    	return 0.0;
	    } else {
	
	    	// Get the docID from the cache
	    	int MinDocID = this.docIteratorGetMatch();
	    	double MinTermFreq = Integer.MAX_VALUE;
	    	int size = this.args.size();
	    	
	    	// Go through all the term, check if the docID is same as 
	    	// cached one and then look get the minimum score i.e. term freq.
	    	for(int i=0;i<size;i++){
	    		
	    		QrySop q_i = (QrySop) this.args.get(i);
	    			
	    			if(q_i.docIteratorHasMatch(r) && q_i.docIteratorGetMatch()==MinDocID){
	    				if(q_i.getScore(r) < MinTermFreq){
	    					MinTermFreq = q_i.getScore(r);
	    				}
	    			}
	    	}
	    	return (MinTermFreq);
	    }
	  }
  
  /**
   *  getScore for the Indri retrieval model for AND query.
   *  Go through all the terms covered by the and look for the cached docID.
   *  Now, get the minimum term frequency of the doc since this is an AND
   *  operator.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  private double getScoreIndri(RetrievalModel r) throws IOException{
	
	    int MinDocID = this.docIteratorGetMatch();
	    double score = 1.0;
	    int size = this.args.size();
	    	
	    for(int i=0;i<size;i++){
	    	QrySop q_i = (QrySop) this.args.get(i);
	    		
	    	if(q_i.docIteratorHasMatch(r) && (q_i.docIteratorGetMatch()==MinDocID)){
	    		score *= q_i.getScore(r);
	    	}else{
	    		score *= q_i.getScoreDefault(r,MinDocID);
	    	}
	    }
	    	
	    return (Math.pow(score,1/(double)size));
  }
  
  @Override
  /**
   *  getScoreDefault for the Indri retrieval model for Nested AND query. 
   *  @param r The retrieval model that determines how scores are calculated.
   *  @param DocID The DocId whose default score needs to be calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreDefault(RetrievalModel r,int DocID)throws IOException{
	  
	  	int size= this.args.size() ;
	  	double score = 1.0;

	  	for(int i=0;i<size;i++){
	  		QrySop q_i = (QrySop)this.args.get(i);
    		score *= q_i.getScoreDefault(r,DocID);	
	  	}
	  	
	  	return (Math.pow(score,1/(double)size));
  }

}
