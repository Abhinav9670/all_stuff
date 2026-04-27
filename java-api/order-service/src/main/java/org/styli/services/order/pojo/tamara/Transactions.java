package org.styli.services.order.pojo.tamara;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

@Data
public class Transactions {

	private List<TamaraCancels> cancels = new ArrayList<>();
	private List<TamaraCaptures> captures = new ArrayList<>();
	private List<TamaraRefunds> refunds = new ArrayList<>();
	
	
}
