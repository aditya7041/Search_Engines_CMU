import java.io.*;
import java.util.ArrayList;

/**
 *  The AND operator for all retrieval models.
 */
public class QrySopWand extends QrySop {

  /**
   *  Indicates whether the query has a match i.e all terms should be 
   *  present in a particular doc. This is taken care by 
   *  docIteratorHasMatchMax(model).
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

	  if (r instanceof RetrievalModelIndri){
        return this.getScoreIndri (r);
      }else {
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support WAND operator.");
    }
  }
  
  private double getScoreIndri(RetrievalModel r) throws IOException{
	
	    int MinDocID = this.docIteratorGetMatch();
	    double score = 1.0,qw=0.0,total_weight=0.0;
	    int size = this.args.size();
	    	
	    for(int i=0;i<size;i++){
	    	total_weight += this.weights.get(i);
	    }
	    
	    for(int i=0;i<size;i++){
	    	QrySop q_i = (QrySop) this.args.get(i);
	    	qw = this.weights.get(i);
	    	
	    	if(q_i.docIteratorHasMatch(r) && (q_i.docIteratorGetMatch()==MinDocID)){
	    		score *= Math.pow(q_i.getScore(r),qw/total_weight);
	    	}else{
	    		score *= Math.pow(q_i.getScoreDefault(r,MinDocID),qw/total_weight);
	    	}
	    }
	    	
	    return score;
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
	  	double score = 1.0,qw=0.0,total_weight=0.0;

	    for(int i=0;i<size;i++){
	    	total_weight += this.weights.get(i);
	    }
	    
	  	for(int i=0;i<size;i++){
	    	QrySop q_i = (QrySop) this.args.get(i);
	    	qw = this.weights.get(i);
    		score *= Math.pow(q_i.getScoreDefault(r,DocID),qw/total_weight);	
	  	}
	  	
	  	return score;
  }

}
