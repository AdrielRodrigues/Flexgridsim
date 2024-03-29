package flexgridsim.rsa;

import java.util.ArrayList;
import java.util.HashMap;

import org.w3c.dom.Element;

import flexgridsim.Flow;
import flexgridsim.LightPath;
import flexgridsim.PhysicalTopology;
import flexgridsim.Slot;
import flexgridsim.TrafficGenerator;
import flexgridsim.VirtualTopology;
import flexgridsim.util.ConnectedComponent;
import flexgridsim.util.KShortestPaths;
import flexgridsim.util.Modulations;
import flexgridsim.util.WeightedGraph;

/**
 * @author pedrom
 *
 */
public class ImageRCSA implements RSA {

	protected PhysicalTopology pt;
	protected VirtualTopology vt;
	protected ControlPlaneForRSA cp;
	protected WeightedGraph graph;
	
	public void simulationInterface(Element xml, PhysicalTopology pt,
			VirtualTopology vt, ControlPlaneForRSA cp, TrafficGenerator traffic) {
		this.pt = pt;
		this.vt = vt;
		this.cp = cp;
		this.graph = pt.getWeightedGraph();
	}

	public void flowArrival(Flow flow) {
		int demandInSlots = (int) Math.ceil(flow.getRate() / (double) pt.getSlotCapacity());
		KShortestPaths kShortestPaths = new KShortestPaths();
		//It returns a vector of nodes
		int[][] kPaths = kShortestPaths.dijkstraKShortestPaths(graph, flow.getSource(), flow.getDestination(), 5);
		boolean[][] spectrum = new boolean[pt.getNumCores()][pt.getNumSlots()];
		
		int modulation = 0;
		
		for (int k = 0; k < kPaths.length; k++) {
				
				int[] links = new int[kPaths[k].length - 1];
				for (int j = 0; j < kPaths[k].length - 1; j++) {
					links[j] = pt.getLink(kPaths[k][j], kPaths[k][j + 1]).getID();
				}
			
				modulation = Modulations.getModulationByDistance(getPhysicalDistance(kPaths[k]));
				demandInSlots = (int) Math.ceil(flow.getRate() / (double) Modulations.getBandwidth(modulation));
					
				for (int i = 0; i < spectrum.length; i++) {
					for (int j = 0; j < spectrum[i].length; j++) {
						spectrum[i][j]=true;
					}
				}
				
				for(int l : links) {
					imageAnd(pt.getLink(l).getSpectrum(), spectrum, spectrum);
				}
				
				/*for (int i = 0; i < kPaths[k].length-1; i++) {
					imageAnd(pt.getLink(kPaths[k][i], kPaths[k][i+1]).getSpectrum(), spectrum, spectrum);
				}*/
					
				//printSpectrum(spectrum)/				
				ConnectedComponent cc = new ConnectedComponent();
				HashMap<Integer,ArrayList<Slot>> listOfRegions = cc.listOfRegions(spectrum);
					
				if (listOfRegions.isEmpty()){
					continue;
				}
				
				if (fitConnection(listOfRegions, demandInSlots, links, flow, modulation))
					return;
		}
		cp.blockFlow(flow.getID());
		return;
	}

	
	/**
	 * @param listOfRegions
	 * @param demandInSlots
	 * @param links
	 * @param flow
	 * @return given a list of rectangles and a demand, the algorithm tries to fit the connector into the spectra
	 */
	public boolean fitConnection(HashMap<Integer,ArrayList<Slot>> listOfRegions, int demandInSlots, int[] links, Flow flow, int modulation){
		ArrayList<Slot> fittedSlotList = new ArrayList<Slot>();
		for (Integer key : listOfRegions.keySet()) {
		    if (listOfRegions.get(key).size()>=demandInSlots){
		    	for (int i = 0; i < demandInSlots; i++) {
		    		fittedSlotList.add(listOfRegions.get(key).get(i));
				}
		    	ArrayList<Slot> fin = new ArrayList<Slot>();
		    	for(int a : links) {
		    		for(Slot fs : fittedSlotList) {
		    			fin.add(new Slot(fs.getCore(), fs.getslot(), a));
		    		}
		    	}
		    	if (establishConnection(links, fin, modulation, flow)){
		    		//System.out.println(modulation);
					return true;
				}
		    }
		}
		return false;
	}

	/**
	 * @param links
	 * @param slotList
	 * @param modulation
	 * @param flow
	 * @return true if the connection was successfully established; false otherwise
	 */
	public boolean establishConnection(int[] links, ArrayList<Slot> slotList, int modulation, Flow flow){
		long id = vt.createLightpath(links, slotList ,modulation);
		if (id >= 0) {
			LightPath lps = vt.getLightpath(id);
			ArrayList<LightPath> lightpath = new ArrayList<LightPath>();
			lightpath.add(lps);
			flow.setLinks(links);
			flow.setSlotList(slotList);
			flow.setModulationLevel(modulation);
			cp.acceptFlow(flow.getID(), lightpath);
			return true;
		} else {
			return false;
		}
	}

	private void imageAnd(boolean[][] img1, boolean[][] img2, boolean[][] res){
		for (int i = 0; i < res.length; i++) {
			for (int j = 0; j < res[0].length; j++) {
				res[i][j] = img1[i][j] & img2[i][j];
			}
		}
	}

	
	public void flowDeparture(Flow flow) {
	}
	
	public int getPhysicalDistance(int[] v) {
		
		int[] links = new int[v.length - 1];
		for (int j = 0; j < v.length - 1; j++) {
			links[j] = pt.getLink(v[j], v[j + 1]).getID();
		}
		
		int sum = 0; 
		for(int a : links) {
			sum += pt.getLink(a).getDistance();
		}
		return sum;
	}
}
