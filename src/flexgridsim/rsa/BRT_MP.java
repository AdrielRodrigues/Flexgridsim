/**
 * Backup, Routing,Modulation Level and Spectrum Assignment
 */
package flexgridsim.rsa;

import java.util.ArrayList;

import org.w3c.dom.Element;

import flexgridsim.Flow;
import flexgridsim.LightPath;
import flexgridsim.Path;
import flexgridsim.PhysicalTopology;
import flexgridsim.Slot;
import flexgridsim.TrafficGenerator;
import flexgridsim.VirtualTopology;
import flexgridsim.util.Modulations;
import flexgridsim.util.MultiGraph;
import flexgridsim.util.WeightedGraph;

/**
 * @author helder
 *
 */
public class BRT_MP  implements RSA  {
	private PhysicalTopology pt;
	private VirtualTopology vt;
	private ControlPlaneForRSA cp;
	private WeightedGraph graph;

	@Override
	public void flowDeparture(Flow flow) {
		// TODO Auto-generated method stub
		
	}
	@Override
	public void simulationInterface(Element xml, PhysicalTopology pt,
			VirtualTopology vt, ControlPlaneForRSA cp, TrafficGenerator traffic) {
		this.pt = pt;
		this.vt = vt;
		this.cp = cp;
		this.graph = pt.getWeightedGraph();	
	}

	@Override
	public void flowArrival(Flow flow) {
		long id=-1;
		long idc;
		int guardBand=1;
		if(pt.getGrooming())
			guardBand=0;
		
		int demandInSlots = (int) (Math.ceil(flow.getRate() / (double) pt.getSlotCapacity()))+ guardBand;
		Path path, pathBack;
		
		ArrayList<Path> multipath = new ArrayList<Path>();

		int mod=5;
		int modulation=0;
		do{
			demandInSlots = (int) Math.ceil(flow.getRate() / (double) Modulations.getBandwidth(mod)) + guardBand;
			MultiGraph multigraph = new MultiGraph(graph, pt, demandInSlots);
			path = getShortestPath(multigraph, flow.getSource(), flow.getDestination(),demandInSlots);
			if(path!=null)
				modulation=Modulations.getModulationByDistance(getPhysicalDistance(path.getLinks()));
			else
				modulation =-1;
			mod--;
		  }while(mod>-1 &&modulation!=-1 && modulation!=mod+1);

		// If no possible path found, block the call
		int[] modulations = new int[2];
		if(path == null) {
			boolean error = false;
			mod = 5;
			do {
				demandInSlots = (int) Math.ceil(flow.getRate() / (double) Modulations.getBandwidth(mod));
				ArrayList<Integer> demandInMP = new ArrayList<Integer>();
				demandInMP = dividir(demandInSlots, 2); // Multipaths
				WeightedGraph newone = new WeightedGraph(graph);
				
				int count = 0;
				for(Integer de : demandInMP) {
					MultiGraph multigraph = new MultiGraph(newone, pt, de);
					Path prov = getShortestPath(multigraph, flow.getSource(), flow.getDestination(),de);
					if(prov != null) {
						multipath.add(prov);
						int mod_temp = Modulations.getModulationByDistance(getPhysicalDistance(prov.getLinks()));
						modulations[count] = mod_temp;
						int[] lks = prov.getLinks();
						for(int l = 0; l < lks.length; l++) {
							newone.removeEdge(pt.getLink(lks[l]).getSource(), pt.getLink(lks[l]).getDestination());
						}
					} else {
						modulations[count] = -1;
					}
					count++;
				}
				mod--;
				
				for(int k = 0; k < 2; k++) {
					if(modulations[k] == -1)
						error = true;
				}
				
				
			}while(mod>-1 && error/* && modulation!=mod+1*/);
			
			for(int k = 0; k < 2; k++) {
				if(modulations[k] == -1)
					multipath.clear();
			}
		}
		
		
		
		if (path == null && multipath.isEmpty()) {
			cp.blockFlow(flow.getID());
			//System.out.println("Linha 67");
			return;
		}/* else if (path.getLinks()==null||path.getSlotList().isEmpty()) {
			cp.blockFlow(flow.getID());
			//System.out.println("linha 71");
			return;
		}*/
		
		if(path != null) {
			id = vt.createLightpath(path, Modulations.getModulationLevel(Modulations.getModulationLevel(mod)));
			//se não achar caminho de proteção lembrar de remover lightpath
			if (id >= 0) {
				flow.setLinks(path.getLinks());
				flow.setSlotList(path.getSlotList());
				flow.setModulationLevel(mod);
			}
			if(id<0){
				cp.blockFlow(flow.getID());
				//System.out.println("linha 89");
				return;
			}
			
	
			int modback=5;
			int modulationback=0;
			int demandInSlotsback;
			//Search existing protection paths that can be shared
			for (int a = 0; a < vt.getnextLightpathID() ; a++) {
				if(vt.getLightpath(a)!=null)
				//busca so caminhos de proteção
					if(vt.getLightpath(a).getProtection()){ // check if this LightPath is a protection path
				//ensures that the paths are disjoint
						if (disjoint(cp.getLightPathsProtectedByLightPath(vt.getLightpath(a)), flow.getLinks())){//disjunto		
							//ensure that the os nós estão no caminho de proteção
							//Diferente				
							if((pt.getLink(vt.getLightpath(a).getLinks()[0]).getSource()==flow.getSource()&&pt.getLink(vt.getLightpath(a).getLinks()[vt.getLightpath(a).getLinks().length-1]).getDestination()==flow.getDestination())
									||(pt.getLink(vt.getLightpath(a).getLinks()[0]).getSource()==flow.getDestination()&&pt.getLink(vt.getLightpath(a).getLinks()[vt.getLightpath(a).getLinks().length-1]).getDestination()==flow.getSource()) ){
								modulationback=vt.getLightpath(a).getModulationLevel();
								demandInSlotsback = (int) Math.ceil(flow.getRate() /Modulations.getBandwidth(modulationback)) + guardBand;
								if(vt.getLightpath(a).getSlotbyLink()<= demandInSlotsback){
									flow.setLinksp(vt.getLightpath(a).getLinks());
									flow.setSlotListp(vt.getLightpath(a).getSlotList());
									flow.setModulationLevelBack(modulationback);
									ArrayList<LightPath> lightpath = new ArrayList<LightPath>(); 
									lightpath.add(vt.getLightpath(id));
									ArrayList<LightPath> lightpathp = new ArrayList<LightPath>(); 
									lightpathp.add(vt.getLightpath(a));
									cp.acceptFlow(flow.getID(),lightpath, lightpathp);
									return;
								}
							}
						}
					}
			  }
			//created a new p-cycle of protection			
	
			modback=5;
			modulationback=0;
			do{
				demandInSlotsback = (int) Math.ceil(flow.getRate() / (double) Modulations.getBandwidth(modback)) + guardBand;
				MultiGraph multigraph = new MultiGraph(graph, pt, demandInSlotsback);
	
				//pathBack=getbestPathBack(multigraph, flow.getSource(), flow.getDestination(),demandInSlotsback, path);
				pathBack=getbestPathBack(flow.getSource(), flow.getDestination(),demandInSlotsback, path);
	
				if(pathBack!=null)
					modulationback=Modulations.getModulationByDistance(getPhysicalDistance(pathBack.getLinks()));
				else
					modback=-1;
				modback--;
			    }while(modback>-1 &&modulationback!=-1 && modulationback!=modback+1);
			
			
			if (pathBack == null || pathBack.getLinks().length<1|| pathBack.getLinks()==null||pathBack.getSlotList()==null||path.getSlotList().isEmpty()) {
				vt.removeLightPath(id);
				cp.blockFlow(flow.getID());
				//System.out.println("linha 121");
			//	System.exit(0);
				return;
			} 
			
			idc = vt.createLightpathProtection(pathBack,Modulations.getModulationLevel(Modulations.getModulationLevel(modback)));
	
			if (idc >= 0) {
				flow.setLinksp(pathBack.getLinks());
				flow.setSlotListp(pathBack.getSlotList());
				vt.getLightpath(idc).setProtection();
				flow.setModulationLevel(modback);
				ArrayList<LightPath> lightpath = new ArrayList<LightPath>(); 
				lightpath.add(vt.getLightpath(id));
				ArrayList<LightPath> lightpathp = new ArrayList<LightPath>(); 
				lightpathp.add(vt.getLightpath(idc));
				cp.acceptFlow(flow.getID(),lightpath, lightpathp);
				//System.out.println("\n\n\n\n\n");
				return;
			}
			vt.removeLightPath(id);
			//System.out.println("linha 142");
			//System.exit(0);
			cp.blockFlow(flow.getID());
		}
		
		if(!multipath.isEmpty()){
			System.out.println("MULTIPATH");
			ArrayList<Long> ids = new ArrayList<Long>();
			for(Path mps : multipath) { 
				ids.add(vt.createLightpath(mps, Modulations.getModulationLevel(Modulations.getModulationLevel(mod))));
			}
			//se não achar caminho de proteção lembrar de remover lightpath
			boolean ctm = false;
			for(Long t : ids) {
				if(t < 0)
					ctm = true;
			}
			if (!ctm) {
				ArrayList<LightPath> lightpath = new ArrayList<>();
				int num = 0;
				for(Path mp : multipath) {
					flow.setLinks(mp.getLinks(), num);
					flow.setSlotList(mp.getSlotList(), num);
					flow.setModulationLevel(modulations[num]); //TODO modulation different
					lightpath.add(vt.getLightpath(ids.get(num)));
					num++;
				}
				
				if(!cp.acceptFlow(flow.getID(), lightpath)) {
					cp.blockFlow(flow.getID());
					return;
				}
				
				return;
				
			}
			if(ctm){
				cp.blockFlow(flow.getID());
				//System.out.println("linha 89");
				return;
			}
		}
	}

	
	public int getPhysicalDistance(int[] links){
		if(links!=null&& links.length>0){
			int physicalDistance = 0;
			for (int i = 0; i < links.length - 1; i++) {
				physicalDistance += pt.getLink(links[i]).getDistance();
			}
			return physicalDistance/2;
		}
		else
			return -1;
	}
	
	/**
	 * Verify if two paths are disjoint
	 * 
	 * @param vector of links
	 *           
	 * @param path 
	 *            path 
	 * @return return true in case disjoint, false other case
	 */	
//	Diz se dois vetores são disjuntos
	/*	public  boolean disjoint(ArrayList<Flow> flows, int[] linkpath) {
	if( flows!=null&&linkpath!=null && linkpath.length!=0){
		for (Flow flow : flows){
			for(int i = 0; i < flow.getLinks().length; i++){  
				for(int j = 0; j < linkpath.length; j++){  
					if( flow.getLinks()[i]==linkpath[j])
							return false;	
				}
			}
		}
	}		
	return true;
}*/
public  boolean disjoint(ArrayList<LightPath> lightpaths, int[] linkpath) {
	if(lightpaths!=null&& linkpath!=null && linkpath.length!=0){
		for (LightPath lightpath : lightpaths){
			for(int i = 0; i < lightpath.getLinks().length; i++){  
				for(int j = 0; j < linkpath.length; j++){  
					if( lightpath.getLinks()[i]==linkpath[j])
							return false;	
				}
			}
		}
	}		
	return true;
}
	/**
	 * Finds, from the list of unvisited vertexes, the one with the lowest
	 * distance from the initial node.
	 * 
	 * @param dist
	 *            vector with shortest known distance from the initial node
	 * @param v
	 *            vector indicating the visited nodes
	 * @return vertex with minimum distance from initial node, or -1 if the
	 *         graph is unconnected or if no vertexes were visited yet
	 */
	public int minVertex(double[] dist, boolean[] v) {
		double x = Double.MAX_VALUE;
		int y = -1; // graph not connected, or no unvisited vertices
		for (int i = 0; i < dist.length; i++) {
			if (!v[i] && dist[i] < x) {
				y = i;
				x = dist[i];
			}
		}
		return y;
	}
	

	
	
	// Dijkstra's algorithm to find shortest path from s to all other nodes
		/**
		 * Msp.
		 *
		 * @param G the g
		 * @param s the s
		 * @param demand the demand
		 * @param protection true if is to protect false otherwise
		 * @return the int[]
		 */
	
public int[] MSP(WeightedGraph G, int s, int demand) {
	final double[] dist = new double[G.size()]; // shortest known distance
												// from "s"
	final int[] pred = new int[G.size()]; // preceding node in path
	final boolean[] visited = new boolean[G.size()]; // all false initially
	for (int i = 0; i < dist.length; i++) {
		pred[i] = -1;
		dist[i] = 1000000;
	}
	dist[s] = 0;
	for (int i = 0; i < dist.length; i++) {
		final int next = minVertex(dist, visited);
		if (next >= 0) {
			visited[next] = true;

			// The shortest path to next is dist[next] and via pred[next].
			final int[] n = G.neighbors(next);
			for (int j = 0; j < n.length; j++) {
				final int v = n[j];
				final double d = dist[next] + G.getWeight(next, v);
				if (dist[v] > d) {
					dist[v] = d;
					pred[v] = next;
				}					
			}
		}
	}
	return pred;
}


	
	/**
	 * Retrieves the shortest path between a source and a destination node,
	 * within a weighted graph.
	 * 
	 * @param G
	 *            the weighted graph in which the shortest path will be found
	 * @param src
	 *            the source node
	 * @param dst
	 *            the destination node
	 * @param demand
	 *            size of the demand
	 * @return the shortest path, as a vector of integers that represent node
	 *         coordinates
	 */


	public ArrayList<Integer> getShortestPath(WeightedGraph G, int src, int dst, int demand) {
		int x;
		ArrayList<Integer> path = new ArrayList<Integer>();
		final int[] pred = MSP(G, src, demand);
		if (pred == null) {
			return null;
		}
		x = dst;
	
		while (x != src) {
			path.add(0, x);
			x = pred[x];
			// No path
			if (x == -1) {
				return null;
			}				
		}
		path.add(0, src);
		
		return path;
	}

	public Path getShortestPath(MultiGraph multigraph, int src, int dst, int demand) {
		int links[];
		int nowSlot=-1;
		int nowCore=-1;;
		ArrayList<Integer> path = new ArrayList<Integer>();
		ArrayList<Slot> channel = new ArrayList<Slot>();
		for (int j = 0; j < multigraph.getNumMultiedges();j++) {	
			for (int i = 0; i < multigraph.getNumEdges(); i++) {
				ArrayList<Integer> nowpath = new ArrayList<Integer>();
				nowpath=getShortestPath(multigraph.getGraph(i,j),src,dst, demand);
				if(nowpath==null||nowpath.size()<2){
					continue;
				}else{
					if(nowpath.size()<path.size()||path.isEmpty()){
						path=nowpath;
						nowSlot=i;
						nowCore =j;
					}
				}
				
			}
		}
		if(path.size()<2){
			return null;
		}
		links = new int[path.size() - 1];

		for (int j = 0; j < path.size() - 1; j++) {
			links[j] = pt.getLink(path.get(j), path.get(j + 1)).getID();
		}
		for (int l = 0; l < links.length; l++) {
			for (int j = nowSlot; j < nowSlot+demand; j++) {
				channel.add(new Slot(nowCore, j, links[l] ));
			}
		}
		return new Path(links, channel);
	}
	
	public  Path getbestPathBack( int src, int dst, int demand,Path path) {
		Path backupPath;
		MultiGraph multigraphb = new MultiGraph(graph, pt, demand);		
		for (int i = 0; i < path.getLinks().length; i++) {	
			multigraphb.removeEdge(pt.getLink(path.getLink(i)).getSource(),pt.getLink(path.getLink(i)).getDestination());
		}	
		 backupPath = getShortestPath(multigraphb, src, dst, demand);
		return backupPath;
	}
	
	
	
	public  Path getbestPathBack(MultiGraph multigraph, int src, int dst, int demand,Path path) {
		Path backupPath;
		for (int i = 0; i < path.getLinks().length; i++) {	
			multigraph.removeEdge(pt.getLink(path.getLink(i)).getSource(),pt.getLink(path.getLink(i)).getDestination());
			multigraph.removeEdge(pt.getLink(path.getLink(i)).getDestination(),pt.getLink(path.getLink(i)).getSource());
		}	
		 backupPath = getShortestPath(multigraph, src, dst, demand);
		return backupPath;
	}
	
	/**
	 * @author Adriel
	 * @param demandTotal
	 * @param multipaths
	 * @return
	 */
	
	public ArrayList<Integer> dividir(int demandTotal, int multipaths){
		ArrayList<Integer> demands = new ArrayList<Integer>();
		demandTotal += multipaths;
		while(multipaths != 0) {
			int dm = (int) Math.ceil(demandTotal / multipaths);
			demands.add(dm);
			demandTotal -= dm;
			multipaths--;
		}
		return demands;
	}
	
}
