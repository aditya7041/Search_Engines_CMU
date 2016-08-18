/**
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */

import java.io.*;
import java.lang.IllegalArgumentException;

/**
 *  The SCORE operator for all retrieval models.
 */
public class QrySopScore extends QrySop {

  /**
   *  Document-independent values that should be determined just once.
   *  Some retrieval models have these, some don't.
   */
  
  /**
   *  Indicates whether the query has a match.
   *  @param r The retrieval model that determines what is a match
   *  @return True if the query matches, otherwise false.
   */
  public boolean docIteratorHasMatch (RetrievalModel r) {
    return this.docIteratorHasMatchFirst (r);
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
    	return this.getScoreRankedBoolean (r);
    }else if(r instanceof RetrievalModelBM25){
    	return this.getScoreBM25 (r);
    }else if(r instanceof RetrievalModelIndri){
    	return this.getScoreIndri(r);
    }else{
      throw new IllegalArgumentException
        (r.getClass().getName() + " doesn't support the SCORE operator.");
    }
  }
  
  public double getScoreDefault(RetrievalModel r, int docid) throws IOException{
	 
	  //System.out.println("Getting default score SopScore");
  	QryIop q_inv = (QryIop) this.args.get(0);
  	double ctf = (double)q_inv.getCtf();
  	double tot_len = (double)Idx.getSumOfFieldLengths(q_inv.field);
  	double doc_length = (double)Idx.getFieldLength(q_inv.field,docid);
	double mu = ((RetrievalModelIndri) r).get_mau();
	double lambda = ((RetrievalModelIndri) r).get_lambda();
  	double prior=ctf/tot_len;
  	
  	return (1-lambda)*mu*prior/(doc_length + mu) + lambda*prior;
  }
  /**
   *  getScore for the Unranked retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreUnrankedBoolean (RetrievalModel r) throws IOException {
    if (! this.docIteratorHasMatchCache()) {
      return 0.0;
    } else {
      return 1.0;
    }
  }

  public int getTermFrequency() throws IOException{
	    if (! this.docIteratorHasMatchCache()) {
	    	return 0;
	      } else {
	    	    QryIop q_inv = (QryIop) this.args.get(0);
				return q_inv.getTf();
	      }
  }
  /**
   *  getScore for the Ranked retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreRankedBoolean (RetrievalModel r) throws IOException {
	  	
	    if (! this.docIteratorHasMatchCache()) {
	      return 0.0;
	    } else if(this.args.get(0) instanceof QryIop){
	    		QryIop q_inv = (QryIop) this.args.get(0);
	    		return q_inv.getTf();
	    	}else{
	    	return 0.0;
	    }
	  }
  
  /**
   *  getScore for the BM25 retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  
  public double getScoreBM25(RetrievalModel r) throws IOException {
	  	
	    if (! this.docIteratorHasMatchCache()) {
	      return 0.0;
	    } else {
	    
	    	QryIop q_inv = (QryIop) this.args.get(0);
	    	double tf = (double)q_inv.tf,df = (double)q_inv.getDf();
	    	double k_1 = ((RetrievalModelBM25) r).get_k1();
	    	double b = ((RetrievalModelBM25) r).get_b();
	    	double k_3 = ((RetrievalModelBM25) r).get_k3();
	    	
	    	double num_docs = (double)Idx.getDocCount(q_inv.field);
	    	double tot_len = (double)Idx.getSumOfFieldLengths(q_inv.field);
	    	double average_len = tot_len/num_docs;
	    	double doc_length = (double)Idx.getFieldLength(q_inv.field,q_inv.docIteratorGetMatch());
	    	
	    	double idf = Math.max(Math.log(num_docs - df +0.50) - Math.log(df+ 0.50),0.00);
	    	double tf_weight = tf/(tf + k_1*((1-b) + (b*doc_length/average_len)));
	     
	    	return idf*tf_weight;
	    }
  }
  
  /**
   *  getScore for the Indri retrieval model.
   *  @param r The retrieval model that determines how scores are calculated.
   *  @return The document score.
   *  @throws IOException Error accessing the Lucene index
   */
  public double getScoreIndri(RetrievalModel r) throws IOException{
	  
	  if (! this.docIteratorHasMatchCache()) {
	      return 0.0;
	    } else {
	    	
	    	QryIop q_inv = (QryIop) this.args.get(0);
	    	double tf = (double)q_inv.tf,ctf = (double)q_inv.getCtf();
	    	double mu = ((RetrievalModelIndri) r).get_mau();
	    	double lambda = ((RetrievalModelIndri) r).get_lambda();
	    	double tot_len = (double)Idx.getSumOfFieldLengths(q_inv.field);
	    	double doc_length = (double)Idx.getFieldLength(q_inv.field,q_inv.docIteratorGetMatch());
	    	double prior = ctf/tot_len;
	    	
	    	return (1- lambda)*(tf+mu*prior)/(doc_length + mu) + (lambda)*prior;
	    }
  }
  
  /**
   *  Initialize the query operator (and its arguments), including any
   *  internal iterators.  If the query operator is of type QryIop, it
   *  is fully evaluated, and the results are stored in an internal
   *  inverted list that may be accessed via the internal iterator.
   *  @param r A retrieval model that guides initialization
   *  @throws IOException Error accessing the Lucene index.
   */
  public void initialize (RetrievalModel r) throws IOException {
    Qry q = this.args.get (0);
    q.initialize (r);
  }

}
