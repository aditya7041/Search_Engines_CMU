/**
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */

/**
 *  An object that stores parameters for the unranked Boolean
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelIndri extends RetrievalModel {

	private double lambda; 
	private double mau;
	private boolean fb;
	private int fbDocs;
	private int fbTerms;
	private double fbMu;
	private double fbOrigWeight;
	private String fbInitialRankingFile;
	private String fbExpansionQueryFile;
	
	public String defaultQrySopName(){
		return new String ("#and");
	}
	
	public RetrievalModelIndri(){
		this.lambda = 0.0;
		this.mau = 0.0;
		this.fb = false;
	}
	
	public RetrievalModelIndri(double lambda, double mau){
		this.lambda = lambda;
		this.mau = mau;
		this.fb=false;
	}
	
	public RetrievalModelIndri(double lambda, double mau,
					int fbDocs,int fbTerms,double fbMu,double fbOrigWeight){
		this.fb=true;
		this.lambda = lambda;
		this.mau = mau;
		this.fbDocs = fbDocs;
		this.fbTerms = fbTerms;
		this.fbMu = fbMu;
		this.fbOrigWeight = fbOrigWeight;
	}
	
	public void setparams(double lambda,double mau){
		this.lambda = lambda;
		this.mau = mau;
	}
	
	public void set_lambda(double lamdba){
		this.lambda = lambda;
	}

	public void set_mau(double mau){
		this.mau = mau;
	}
	
	public void set_fb(boolean fb){
		this.fb = fb;
	}
	
	public void set_fbDocs(int fbDocs){
		this.fbDocs = fbDocs;
	}
	
	public void set_fbTerms(int fbTerms){
		this.fbTerms = fbTerms;
	}
	
	public void set_fbMu(double fbMu){
		this.fbMu = fbMu;
	}
	
	public void set_fbOrigWeight(double w){
		this.fbOrigWeight = w;
	}
	
	public void set_fbInitialRankingFile(String s){
		this.fbInitialRankingFile = s;
	}
	
	public void set_fbExpansionQueryFile(String s){
		this.fbExpansionQueryFile = s;
	}
	
	public boolean get_fb(){
		return this.fb;
	}
	
	public double get_lambda(){
		return this.lambda;
	}
	
	public double get_mau(){
		return this.mau;
	}
	
	public double get_fbMu(){
		return this.fbMu;
	}
	
	public int get_fbDocs(){
		return this.fbDocs;
	}

	public int get_fbTerms(){
		return this.fbTerms;
	}
	
	public double fbOrigWeight(){
		return this.fbOrigWeight;
	}
	
	public String get_fbInitialRankingFile(){
		return this.fbInitialRankingFile;
	}
	
	public String get_fbExpansionQueryFile(){
		return this.fbExpansionQueryFile;
	}
	
	@Override
	public void printParams() {
		System.out.println("Indri : lambda " + this.lambda + " mau = " + this.mau);
	}
}