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
	
	public String defaultQrySopName(){
		return new String ("#and");
	}
	
	public RetrievalModelIndri(){
		this.lambda = 0.0;
		this.mau = 0.0;
	}
	
	public RetrievalModelIndri(double lambda, double mau){
		this.lambda = lambda;
		this.mau = mau;
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
	
	public double get_lambda(){
		return this.lambda;
	}
	
	public double get_mau(){
		return this.mau;
	}

	@Override
	public void printParams() {
		System.out.println("lambda " + this.lambda + " mau = " + this.mau);
		
	}
}