/** 
 *  Copyright (c) 2016, Carnegie Mellon University.  All Rights Reserved.
 */
import java.util.*;

//a comparator that compares Strings
class ValueComparator implements Comparator<String>{

	LinkedHashMap<String,Double> map = new LinkedHashMap<String,Double>();

	public ValueComparator(HashMap<String,Double> hmap){
		this.map.putAll(hmap);
	}

	@Override
	public int compare(String s1, String s2) {
		if(map.get(s1) >= map.get(s2)){
			return -1;
		}else{
			return 1;
		}	
	}
}