package se.facilia.bsa2020.Screening;

import java.beans.PropertyVetoException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import javax.swing.SwingUtilities;

import se.facilia.ecolego.domain.EcolegoIndexList;
import se.facilia.ecolego.domain.EcolegoParameter;
import se.facilia.ecolego.domain.EntryDataObject;
import se.facilia.ecolego.domain.ParameterEntry;
import se.facilia.ecolego.domain.model.DataObjectFactory;
import se.facilia.facilialib.domain.common.DefaultIndex;
import se.facilia.facilialib.domain.common.IDataObject;
import se.facilia.math.stat.samp.SampUtil;
import se.facilia.math.stat.samp.dist.Logn;
import se.facilia.math.stat.samp.dist.Logn4;
import se.facilia.math.stat.samp.dist.Unif;




public class ModelBuilderScreening extends ModelBuilderBase {


	final static int nGridPoints = 20000;
	
	private static final String projectName = "Model Area Screening Grid";	
	private static File workDir = new File("D:\\Facilia SVN D\\POSIVA\\Screening modelling in TURVA-2020\\4_BSA-2020_AREA\\KS");
	
	public static void main(String[] args) throws Exception {
		ModelBuilderScreening sc = new ModelBuilderScreening();
			
		
					//sc.createTestGrid();
					//sc.save();
					sc.enableGridIndices();
					sc.importTestData();
					sc.save();
				
					System.out.println("done");
				
				
		
	}
	public ModelBuilderScreening() throws Exception {
		super(workDir, projectName, false);
		
		
		
		
	}
	
	private void enableGridIndices() {
		
		EcolegoIndexList gridIndexList = getProject().getDataModel().getIndexListModel().getByName("Grid");
		getProject().getDataModel().setValidationEnabled(false);
		
		
		int nEnable = 500;
		for (int k=0; k < gridIndexList.size(); ++k)  {
			if (k < nEnable) {
				gridIndexList.get(k).setEnabled(true);	
			} else {
				gridIndexList.get(k).setEnabled(false);
			}
			
		}
		
		
		getProject().getDataModel().setValidationEnabled(true);
		
		
		
				
		
	}

	
	private void createTestGrid() throws PropertyVetoException {
		System.out.println("create grid");
		EcolegoIndexList gridIndexList = getProject().getDataModel().getIndexListModel().getByName("Grid");
		getProject().getDataModel().setValidationEnabled(false);		
				
		gridIndexList.clear();
		
//		EcolegoIndexList newList = new EcolegoIndexList("Grid");		
//		List<EntryDataObject<?>> depBlocks = getFinder().getByIndexList(gridIndexList);	
//		ArrayList<String> depNames = new ArrayList<String>();
//		for (int m=0; m < depBlocks.size(); ++m) {
//			EntryDataObject<?> b = depBlocks.get(0);
//			depNames.add(b.getId());
//		}
//		getProject().getDataModel().getIndexListModel().remove(gridIndexList);
//		
//		for (String id: depNames) {					
//			EntryDataObject<?> b = (EntryDataObject<?>) getFinder().getById(id);
//			System.out.println("iterating " + DataObjectFactory.getTypeName(b.getClass()) + " " +b.getId()+ ". Number of blocks left " +  depBlocks.size());
//			
//			EcolegoIndexList[] lists = new EcolegoIndexList[b.getDimension()+1];
//			lists[lists.length-1] = gridIndexList;
//			
//			b.setIndexLists(lists);
//		}
		
		for (int c=0; c < nGridPoints; ++c) {
			DefaultIndex index = new DefaultIndex("c" + c);
			gridIndexList.add(index);
			System.out.println("adding indexx " + index.getId());
			if (c % 1000 == 0) {
				System.out.println("adding grid point " + c);	
			}
			
		}
		
		
		getProject().getDataModel().setValidationEnabled(true);
		System.out.println("done create grid");
		
		
	}
	
	private void importTestData() throws IOException {
		System.out.println("import test data");
		importTestKdValues();
		System.out.println("done import test data");
	}

	private void importTestKdValues() throws IOException {
		
		getProject().getDataModel().setValidationEnabled(false);;
		SampUtil.setSeed(100L);
		double gm = 1;
		double gsd = 100;
		Logn4 logn = new Logn4(gm,gsd);
		int N = (int)1000;
		double[] randKd = logn.rand(N);
		
		int c=0;
		List<IDataObject> kds = getFinder().find("Kd_Deep", "Grid.*data", DataObjectFactory.getTypeName(EcolegoParameter.class));
		
		for (IDataObject d : kds) {			
			EcolegoParameter p = (EcolegoParameter) d;			
			Iterator<Object[]> indit = p.iterateIndices();
			while (indit.hasNext()) {
				Object[] indices = indit.next();
				int ii = c++ % randKd.length;				
				double value=randKd[ii];		
				ParameterEntry entry = p.get(indices);				
				if (entry == null) {
					entry = new ParameterEntry(value);
					p.put(entry, indices);
				}			
				entry.setValue(value);								
				if (c % 100 == 0) {
					System.out.println("importing " + d.getId() + " + " + c);	
				}
				
				
			}
		}
		getProject().getDataModel().setValidationEnabled(true);;
		
		
		
	}
	
	
	
}
