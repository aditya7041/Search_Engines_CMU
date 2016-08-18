/**
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */

/**
 *  An object that stores parameters for the unranked Boolean
 *  retrieval model (there are none) and indicates to the query
 *  operators how the query should be evaluated.
 */
public class RetrievalModelBM25 extends RetrievalModel {

	private double k_1;
	private double b;
	private double k_3;

	public String defaultQrySopName () {
		return new String ("#sum");
	}
	
	public RetrievalModelBM25(){
		this.k_1= 0.0;
		this.b = 0.0;
		this.k_3 = 0.0;
	}

	public RetrievalModelBM25(double k_1,double b,double k_3){
		this.k_1= k_1;
		this.b = b;
		this.k_3 = k_3;
	}
	
	public void set_params(double k_1,double b, double k_3){
		this.k_1 = k_1;
		this.b = b;
		this.k_3 = k_3;
	}
	
	public void set_k1(double k_1){
		this.k_1 = k_1;
	}

	public void set_b(double b){
		this.b = b;
	}
	
	public void set_k3(double k_3){
		this.k_3 = k_3;
	}
	
	public double get_k1(){
		return this.k_1;
	}

	public double get_b(){
		return this.b;
	}
	
	public double get_k3(){
		return this.k_3;
	} 

	@Override
	public void printParams() {
		System.out.println(" MBM25 Params : k_1" + this.k_1 + " b " + 
				this.b + " k_3 " + this.k_3);
	}
}