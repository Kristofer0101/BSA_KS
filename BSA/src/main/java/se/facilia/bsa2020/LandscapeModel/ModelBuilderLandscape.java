package se.facilia.bsa2020.LandscapeModel;

import java.awt.Desktop;
import java.awt.datatransfer.Transferable;
import java.beans.PropertyVetoException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import javax.swing.TransferHandler;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import com.google.common.collect.Sets;

import se.facilia.bsa2020.Screening.ModelBuilderBase;
import se.facilia.ecolego.domain.Compartment;
import se.facilia.ecolego.domain.Connector;
import se.facilia.ecolego.domain.EcolegoIndexList;
import se.facilia.ecolego.domain.EcolegoParameter;
import se.facilia.ecolego.domain.EntryDataObject;
import se.facilia.ecolego.domain.Equation;
import se.facilia.ecolego.domain.Expression;
import se.facilia.ecolego.domain.ExpressionEntry;
import se.facilia.ecolego.domain.GroupSubSystem;
import se.facilia.ecolego.domain.LookupTable;
import se.facilia.ecolego.domain.LookupTableEntry;
import se.facilia.ecolego.domain.SubSystemBlock;
import se.facilia.ecolego.domain.Transfer;
import se.facilia.ecolego.domain.TransferEntry;
import se.facilia.ecolego.domain.block.IBlock;
import se.facilia.ecolego.domain.block.IComponent;
import se.facilia.ecolego.domain.datatransfer.TransferManager;
import se.facilia.ecolego.domain.model.DataModel;
import se.facilia.ecolego.domain.model.DataObjectFactory;
import se.facilia.ecolego.domain.model.DataObjectFinder;
import se.facilia.ecolego.domain.model.EcolegoProject;
import se.facilia.ecolego.main.Initializer;
import se.facilia.ecolego.presentation.model.IAutoAddFilter;
import se.facilia.ecolego.presentation.model.PresentationModel;
import se.facilia.ecolego.presentation.model.MatrixTreeConnectionModel.CreationMode;
import se.facilia.ecolego.presentation.model.PresentationObjectFactory;
import se.facilia.ecolego.ui.workbench.views.graph.GraphInfo;
import se.facilia.ecolego.ui.workbench.views.matrix.MatrixCellInfo;
import se.facilia.facilialib.db.ExcelUtil;
import se.facilia.facilialib.domain.common.DefaultIndex;
import se.facilia.facilialib.domain.common.GUID;
import se.facilia.facilialib.domain.common.IDataObject;
import se.facilia.facilialib.domain.common.IMaterial;
import se.facilia.facilialib.domain.common.ISubSystem;
import se.facilia.facilialib.domain.common.Pair;
import se.facilia.facilialib.domain.workspace.IProject;
import se.facilia.facilialib.domain.workspace.Workspace;
import se.facilia.facilialib.util.ArrayUtil;
import se.facilia.facilialib.util.CollectionUtil;

/**
 * Builds the Ecolego landscape model Object connections and massinheritence
 * contained in excel files. The same format as BSA2012 currently assumed.
 * 
 * TODO: Mass inheritence flux from the thee uppermost layers are bundled as one
 * number in the file massinheritence.xlsx. How to which amount to transfer from
 * each of the layers?
 * 
 * @author Kristofer
 *
 */
public class ModelBuilderLandscape extends ModelBuilderBase {

	/**
	 * Name of the created project
	 */
	private static final String projectName_Landscape = "BSA2020_test";

	/**
	 * Directories and file names
	 */
	static File workDir = new File("D:\\Facilia SVN D\\POSIVA\\Coupled model");
	static File dataDir = new File(workDir, "data");
	private File libDir = new File("D:\\Facilia SVN D\\POSIVA\\Coupled model\\BSA2012 library");
	String libName = "POSIVA BSA 2020 library";

	/**
	 * Object connection data
	 */
	private File file_object_connections = new File(dataDir, "object_connections.xlsx");
	//private String sheet_connections = "Sample";
	private String sheet_connections = "Real";
	//private String sheet_connections = "H";

	/**
	 * Mass inheritence data
	 */
	private File massfile = new File(dataDir, "massinheritance.xlsx");

	/**
	 * Time points for which there is data
	 */
	double[] times = new double[] { 2020, 2520, 3020, 3520, 4020, 4520, 5020, 5520, 6020, 6520, 7020, 7520, 8020, 8520, 9020, 9520, 10020, 10520, 11020, 11520, 12020 };
	

	/**
	 * Instance fields
	 */
	private EcolegoProject libProject;
	private DataObjectFinder libFinder;
	private DataObjectFactory dataObjectFactory;
	private HashMap<String, String> object_connections;

	public static void main(String[] args) throws Exception {
		boolean createOrOverwrite = true;
		ModelBuilderLandscape b = new ModelBuilderLandscape(projectName_Landscape, createOrOverwrite);

		if (createOrOverwrite) {
			b.addMaterials();	
			b.readData();		
			b.build();
		}

		b.populateParameterData();
		b.save();
		Desktop.getDesktop().open(workDir);
	}

	private void addMaterials() {
		addNewMaterials(libProject, getProject());

	}

	private void readData() throws InvalidFormatException, IOException {
		readConnections();

	}

	private void populateParameterData() throws InvalidFormatException, IOException {

		populateArea("cropland_I","I");
		populateArea("cropland_N","N");
		populateArea("forest","F");
		populateArea("pasture","P");
		populateArea("lake","W");
		populateArea("lake","R");
		populateArea("river","W");
	}

	private void populateArea(String sheetName, String ecoAbbrev) throws InvalidFormatException, IOException {
		System.out.println("pop area frmo " + sheetName + " " + ecoAbbrev);
		File f = new File(dataDir, "area.xlsx");
		Workbook wb = ExcelUtil.readWorkbook(f);
		//String ecoAbbrev = "I";
		Sheet sh = wb.getSheet(sheetName);
		double[] eco_sums = new double[times.length];
		String eco_last = null;
		for (int r = 0; r < sh.getLastRowNum(); ++r) {
			Row row = sh.getRow(r);
			int cell_bso = 0;
			String bsoFrom_orig = row.getCell(cell_bso).getStringCellValue();			
			String bsoFrom = bsoFrom_orig.substring(0, bsoFrom_orig.indexOf("_"));			
			String ecoTypeAbbrev = bsoFrom_orig.substring(bsoFrom_orig.indexOf("_")+1,bsoFrom_orig.length());
			if (!ecoTypeAbbrev.equals(ecoAbbrev)) {
				continue;
			}
			
			
			int cell_ecoType = ecoAbbrev.equals("P") ? 0 : 1;
			String ecoType = row.getCell(cell_ecoType).getStringCellValue();

			for (int it = 0; it < times.length; ++it) {
				double value = row.getCell(cell_ecoType + it + 1).getNumericCellValue();
				eco_sums[it] += value;
			}

			boolean sumUp = r == sh.getLastRowNum();

			if (!sumUp) {
				Row row_peek = sh.getRow(r + 1);
				String bso_peek = row_peek.getCell(cell_bso).getStringCellValue();
				bso_peek = bso_peek.substring(0, bso_peek.indexOf("_"));
				if (!bso_peek.equals(bsoFrom)) {
					sumUp = true;
				}
			}
			
			if (sumUp) {
				ISubSystem ss = (ISubSystem) getFinder().findByName(getProject().getDataModel().getRootSubSystem(), bsoFrom, false);
				if (ss == null) {
					System.err.println("bso not created: " + bsoFrom);
					continue;

				}
				ISubSystem ecoSystem = (ISubSystem) getFinder().getByName(ss, ecoAbbrev);
				LookupTable area = (LookupTable) getFinder().getByName(ecoSystem, "Area");
				area.put(new LookupTableEntry(CollectionUtil.asList(times), CollectionUtil.asList(eco_sums)));

				eco_last = ecoType;
				eco_sums = new double[times.length];

			}

		}

	}

	public ModelBuilderLandscape(String projectName, boolean createOrOverwrite) throws Exception {
		super(workDir, projectName, createOrOverwrite);

		libProject = loadLibrary();

		dataObjectFactory = getProject().getDataModel().getFactory();
		
	}

	private EcolegoProject loadLibrary() throws IOException {
		EcolegoProject libProject = loadProject(libDir, libName);
		libFinder = libProject.getDataModel().getFinder();
		return libProject;

	}

	private void build() throws Exception {

		getProject().getDataModel().setValidationEnabled(false);

		addIndexLists();

		addBSOs();

		// addCoast("SampleCoast001");
		// addCoast("SampleCoast002");

		addMassInhTransfers();
		addTransfersBetweenBSOs();

		getProject().getDataModel().setValidationEnabled(true);

		save();
		System.out.println("Done building!");

	}

	// private void addTransfersFromUpstreamWithinBSO(ISubSystem bso_system) {
	//
	//
	// String[] ecosystem_names = new String[]{"W","R");
	//
	// ISubSystem globalsSystem = (ISubSystem)getFinder().getByName(bso_system,
	// "Globals");
	// for (String ecosystem_name: ecosystem_names) {
	// ISubSystem ecosystem = (ISubSystem)getFinder().getByName(bso_system,
	// ecosystem_name);
	// if (ecosystem != null) {
	//
	//
	// String conn_name = "" + ecosystem_name + "_upstream";
	// IComponent input_block = (IComponent)getFinder().getByName(bso_system,
	// "Input");
	// IComponent output_block= (IComponent)getFinder().getByName(bso_system,
	// "Output");
	// Connector connector = dataObjectFactory.createConnector(conn_name,
	// output_block, input_block);
	// connector.setParent(bso_system);
	//
	// IDataObject source_block =getFinder().getByName(ecosystem,
	// "Flux_Out_Total");
	// IDataObject target_block =getFinder().getByName(globalsSystem,
	// "Flux_Downstream");
	// Pair<GUID> pair = new Pair(source_block.getGUID(),
	// target_block.getGUID());
	//
	// connector.addModelConnection(pair);;
	//
	// getProject().getDataModel().getBlockModel().add(connector);
	// }
	//
	// }
	//
	//
	// }

	/**
	 * Create connectors connecting the outgoing water flux from (the
	 * Flux_Out_Total block) each ecosystem to the (Flux_to_Downstream block) in
	 * its BSO subsystem. After this is done, the water fluxes between BSOs can
	 * be created
	 */
	private void addTransfersToDownstreamWithinBSO(ISubSystem bso_system) throws Exception {

		String[] ecosystem_names = new String[] { "P", "N", "I", "F", "M" };

		ISubSystem BSO_in_system = (ISubSystem) getFinder().getByName(bso_system, "BSO_In");
		ISubSystem BSO_out_system = (ISubSystem) getFinder().getByName(bso_system, "BSO_Out");
		for (String ecosystem_name : ecosystem_names) {
			ISubSystem ecosystem = (ISubSystem) getFinder().getByName(bso_system, ecosystem_name);
			if (ecosystem != null) {

				{

					String conn_name = "" + ecosystem_name + "_downstream";
					IComponent from_block = (IComponent) getFinder().getByName(ecosystem, "Output");
					IComponent to_block = (IComponent) getFinder().getByName(BSO_out_system, "Input");
					Connector connector = dataObjectFactory.createConnector(conn_name, from_block, to_block);
					connector.setProperty(IBlock.PROPERTY_NAME_VISIBLE, true);
					connector.setSubSystem(bso_system);

					IDataObject source_block = getFinder().getByName(ecosystem, "Flux_Out_Total");
					IDataObject target_block = getFinder().getByName(BSO_out_system, "Flux_to_Downstream");
					Pair<GUID> pair = new Pair(source_block.getGUID(), target_block.getGUID());

					if (source_block == null || target_block == null) {
						throw new Exception("block is null");
					}
					connector.addModelConnection(pair);

					getProject().getDataModel().getBlockModel().add(connector);

				}

			}

		}

		// Add connector from Upstream inputs to lake
		SubSystemBlock lake_system = (SubSystemBlock) getFinder().getByName(bso_system, "W");

		if (lake_system != null) {
			String conn_name = "Upstream_to_lake";
			IComponent from_block = (IComponent) getFinder().getByName(BSO_in_system, "Output");
			IComponent to_block = (IComponent) getFinder().getByName(lake_system, "Input");
			Connector connector = dataObjectFactory.createConnector(conn_name, from_block, to_block);
			connector.setSubSystem(bso_system);
			connector.setProperty(IBlock.PROPERTY_NAME_VISIBLE, true);

			IDataObject source_block = getFinder().getByName(BSO_in_system, "Flux_from_Upstream_to_Lake");
			IDataObject target_block = getFinder().getByName(lake_system, "Flux_In_Water");
			Pair<GUID> pair = new Pair(source_block.getGUID(), target_block.getGUID());

			if (source_block == null || target_block == null) {
				throw new Exception("block is null");
			}
			connector.addModelConnection(pair);
			getProject().getDataModel().getBlockModel().add(connector);
		}

	}

	/**
	 * Create a transfer from the Downstream compartment in the source BSO to
	 * the Upstream compartment in the target BSO The routing of activity from
	 * individual ecosystems is handled in the source BSO The routing of
	 * activity to the water compartments in handled in the target BSO TODO:
	 * Possible check if the target BSO has a water compartment, else give error
	 * since data about connections is wrong.
	 * 
	 * @throws Exception
	 * 
	 */
	private void addTransfersBetweenBSOs() throws Exception {

		System.out.println("Adding downstream transfers...");
		ISubSystem root = getProject().getDataModel().getRootSubSystem();
		for (Entry<String, String> e : object_connections.entrySet()) {

			String bso_source_name = e.getKey();
			String bso_target_name = e.getValue();

			ISubSystem from_bso_Output = (ISubSystem) getFinder().getByName(root, bso_source_name + ".BSO_Out");
			ISubSystem to_bso_Input = (ISubSystem) getFinder().getByName(root, bso_target_name + ".BSO_In");

			if (from_bso_Output == null || to_bso_Input == null) {
				System.err.println("BSO did not exist in model: " + bso_source_name + ", " + bso_target_name);
				continue;
			}

			IComponent from_output_block = (IComponent) getFinder().getByName(from_bso_Output, "Output");
			IComponent to_input_block = (IComponent) getFinder().getByName(to_bso_Input, "Input");

			IDataObject target_block = getFinder().getByName(to_bso_Input, "Flux_from_Upstream");
			IDataObject source_block = getFinder().getByName(from_bso_Output, "Flux_to_Downstream");

			// Compartment to_compartment = (Compartment)
			// getFinder().getByName(to_bso, "Upstream");
			// Compartment from_compartment = (Compartment)
			// getFinder().getByName(from_bso, "Downstream");

			String conn_name = bso_source_name + "_to_" + bso_target_name;
			// Transfer tf = dataObjectFactory.createTransfer(tf_name,
			// from_compartment, to_compartment);
			Connector conn = dataObjectFactory.createConnector(conn_name, from_output_block, to_input_block);
			conn.setSubSystem(root);

			Pair<GUID> pair = new Pair(source_block.getGUID(), target_block.getGUID());

			if (source_block == null || target_block == null) {
				throw new Exception("block is null");
			}
			conn.addModelConnection(pair);

			System.out.println("Adding Connection " + conn.getId());

			getProject().getDataModel().getBlockModel().add(conn);

		}

	}

	private ISubSystem addBSOIn(ISubSystem ss) throws IOException {
		return addFromLibToModel(ss, "BSO_In");
	}

	private ISubSystem addBSOOut(ISubSystem ss) throws IOException {
		return addFromLibToModel(ss, "BSO_Out");
	}

	/**
	 * Copy Objects index list from library. Reset it and fill it with fresh
	 * IDs.
	 */
	private void addIndexLists() {
		EcolegoIndexList list = libProject.getDataModel().getIndexListModel().getByName("Objects");
		copyObject(libProject, getProject(), list);

		list = getProject().getDataModel().getIndexListModel().getByName("Objects");

		list.clear();

		for (Entry<String, String> e : object_connections.entrySet()) {
			if (list.getByName(e.getKey()) == null) {
				list.add(new DefaultIndex(e.getKey()));
			}
			if (list.getByName(e.getValue()) == null) {
				list.add(new DefaultIndex(e.getValue()));
			}
		}

	}

	/**
	 * Create and add a BSO subsystem for each BSO id occuring in the object
	 * connection data.
	 * 
	 * @throws Exception
	 */
	private void addBSOs() throws Exception {

		Set<String> objects = new HashSet<String>();
		for (Entry<String, String> entry : object_connections.entrySet()) {

			String source = entry.getKey();
			String target = entry.getValue();

			objects.add(source);
			objects.add(target);
		}

		int count = 0;
		for (String id : objects) {
			System.out.println("creatin BSO nr " + (++count) + ": " + id);
			ISubSystem bso_system = addBSO(id);
			addTransfersToDownstreamWithinBSO(bso_system);

		}

	}

	/**
	 * Adds the mass inheritence transfers between soil layers within the super
	 * super objects. TODO: Currently ignores transfer from DL, OAL, UML in
	 * forest since it is not clear how to distribute the transfer between the
	 * upper layers.
	 * 
	 * @throws InvalidFormatException
	 * @throws IOException
	 */
	private void addMassInhTransfers() throws InvalidFormatException, IOException {
		Workbook wb = readMassInh();

		// human readable name to ecolego name
		HashMap<String, String> layerNameMap = new HashMap<String, String>();
		layerNameMap.put("decomposition layer", "DL");
		layerNameMap.put("organic accumulation layer", "OAL");
		layerNameMap.put("upper mineral layer", "UML");
		layerNameMap.put("middle mineral layer", "MML");
		layerNameMap.put("deep overburden", "DO");
		layerNameMap.put("decomposition layer and organic accumulation layer and upper mineral layer", "UML+"); // Forest.
		layerNameMap.put("organic accumulation layer and upper mineral layer", "OAL+"); // Forest

		// source layer to destination layer
		HashMap<String, String> layerTransMap = new HashMap<String, String>();
		layerTransMap.put("DL", "UML");
		layerTransMap.put("OAL", "UML");
		// layerTransMap.put("UML+", "UML");
		// layerTransMap.put("OAL+", "UML");
		layerTransMap.put("UML", "UML");
		layerTransMap.put("MML", "MML");
		layerTransMap.put("DO", "DO");

		int firstValidSheet = 3;
		for (int s = firstValidSheet; s < wb.getNumberOfSheets(); ++s) {
			Sheet sh = wb.getSheetAt(s);
			nextTransfer: for (int r = 0; r <= sh.getLastRowNum(); ++r) {

				try {

					Row row = sh.getRow(r);

					int COL_BSO_FROM = 0;
					int COL_BSO_TO = 1;
					int COL_COMP_FROM = 2;
					// String DEFAULT_DESTINATION_COMPARTMENT =
					// "Upper mineral layer";

					String bso_from = row.getCell(COL_BSO_FROM).getStringCellValue();
					String bso_to = row.getCell(COL_BSO_TO).getStringCellValue();
					String layer_from_hr = row.getCell(COL_COMP_FROM).getStringCellValue();
					String layer_from_eco = layerNameMap.get(layer_from_hr);

					String layer_to_eco = layerTransMap.get(layer_from_eco);

					if (layer_to_eco == null || layer_from_eco == null) {
						System.err.println("Could not create mass ineheritence transfer between " + layer_from_eco + " to " + layer_to_eco
								+ ". Possible because some BSO is missing from the model!");
						continue nextTransfer;
					}

					String bso_id = bso_from.substring(0, bso_from.indexOf("_"));
					String biotope_from = bso_from.substring(bso_from.indexOf("_") + 1, bso_from.length());

					String biotope_to = bso_to.substring(bso_to.indexOf("_") + 1, bso_to.length());

					String tf_name = "TF_" + biotope_from + "_" + biotope_to + "_" + layer_from_eco + "_" + layer_to_eco;

					ISubSystem bso_system = (ISubSystem) getFinder().getById(bso_id);

					if (bso_system == null) {
						continue nextTransfer;
					}
					IComponent layer_from_comp = (IComponent) getFinder().getById(bso_id + "." + biotope_from + "." + layer_from_eco);
					IComponent layer_to_comp = (IComponent) getFinder().getById(bso_id + "." + biotope_to + "." + layer_to_eco);

					if (layer_from_comp == null) {
						System.err.println("Could not create mass ineheritence transfer between " + layer_from_eco + " to " + layer_to_eco + ".Soil layer " + layer_from_comp
								+ " does not exist in " + biotope_from);
						continue nextTransfer;
					}
					if (layer_to_comp == null) {
						System.err.println("Could not create mass ineheritence transfer between " + layer_from_eco + " to " + layer_to_eco + ".Soil layer " + layer_to_comp
								+ " does not exist in " + biotope_to);
						continue nextTransfer;
					}
					try {
						Transfer tf = dataObjectFactory.createTransfer(tf_name, layer_from_comp, layer_to_comp);
						// tf.get().setMultiplyWithDonor(true);

						String eq = "if(Area<=aeps,0.0," + "MassInh_" + layer_from_eco + "/(depth_" + layer_from_eco + "*Area*dens_" + layer_from_eco + ")*" + layer_from_eco + ")";
						tf.put(new TransferEntry(new Equation(eq)));

						System.out.println("Adding MI TF " + tf.getId());

						getProject().getDataModel().getBlockModel().add(tf);
					} catch (Exception e) {
						System.err.println("Could not add MI Transfer " + tf_name);
						e.printStackTrace();
					}

				} catch (Exception e) {
					e.printStackTrace();
					// System.err.println("Could not create MI transfer for row "
					// + r);
				}

			}

		}

	}

	/**
	 * Read mass inheritence data file
	 * 
	 * @return
	 * @throws InvalidFormatException
	 * @throws IOException
	 */
	private Workbook readMassInh() throws InvalidFormatException, IOException {
		Workbook wb = ExcelUtil.readWorkbook(massfile);
		return wb;

	}

	/**
	 * Read upstream-downstream object mapping data from the object connections
	 * data file
	 * 
	 * @throws InvalidFormatException
	 * @throws IOException
	 */
	private void readConnections() throws InvalidFormatException, IOException {
		Workbook wb = ExcelUtil.readWorkbook(file_object_connections);
		Sheet sh = wb.getSheet(sheet_connections);

		Iterator<Row> ri = sh.rowIterator();
		object_connections = new HashMap<String, String>();
		while (ri.hasNext()) {
			Row row = ri.next();
			String from_object = row.getCell(0).getStringCellValue();
			String to_object = row.getCell(1).getStringCellValue();
			object_connections.put(from_object, to_object);
		}
		System.out.println("" + object_connections.size() + " object connections read!");
	}

	private ISubSystem addBSO(String name) throws PropertyVetoException, IOException {

		SubSystemBlock ss = createSubSystem(name);

		List<IDataObject> children = new ArrayList<IDataObject>();

		// children.add(addHelperBSOBlock(ss));
		// children.add(addHelperBSOBlock(ss));
		children.add(addBSOIn(ss));
		children.add(addCroplandI(ss));
		children.add(addCroplandN(ss));
		children.add(addPastureland(ss));
		children.add(addForest(ss));
		children.add(addWetland(ss));
		children.add(addOpenLake(ss));
		children.add(addReedLake(ss));
		children.add(addBSOOut(ss));

		getProject().getPresentationModel().getTreeConnectionModel().rearrange(ss, children);

		// Workspace.getWorkspace().saveProject(libProject.getName());
		return ss;

	}

	/**
	 * Add a subsystem for the BSO to contain expressions used to indicate if
	 * the BSO has a given ecosystem. The indicators are 1 if the ecosystem
	 * exists and has area > 0, else it is 0.
	 * 
	 * @param ss
	 * @return
	 */
	private IDataObject addHelperBSOBlock(SubSystemBlock ss_bso) {

		SubSystemBlock ss_h = createSubSystem("Helpers", ss_bso);

		List<IDataObject> children = new ArrayList<IDataObject>();

		children.add(createEcosystemIndicator(ss_h, "hasLake", "W"));
		children.add(createEcosystemIndicator(ss_h, "hasReed", "R"));
		children.add(createEcosystemIndicator(ss_h, "hasCroplandIrrigated", "N"));
		children.add(createEcosystemIndicator(ss_h, "hasCroplandNonIrrigated", "I"));
		children.add(createEcosystemIndicator(ss_h, "hasForest", "F"));
		getProject().getPresentationModel().getTreeConnectionModel().rearrange(ss_h, children);

		return ss_h;

	}

	/**
	 * Create a expression indicating wehter a specific ecosystem exists (i.e.
	 * has area>0) For example createEcoSystemIndicator(ss_h, "hasReed","R")
	 * created hasReed in the subsystem ss_h which is 1 if there is a reed with
	 * area > 0 in the bso.
	 */
	private Expression createEcosystemIndicator(ISubSystem subSystem_helper, String name, String ecosystemBlockName) {

		ISubSystem ss_bso = ((ISubSystem) subSystem_helper.getSubSystem());
		Expression hasReedE = dataObjectFactory.createExpression(name, subSystem_helper, new EcolegoIndexList[0]);
		Equation eqR = new Equation("if(" + ss_bso.getId() + "." + ecosystemBlockName + ".Area>0,1,0)");
		hasReedE.put(new ExpressionEntry(eqR));
		getProject().getDataModel().getBlockModel().add(hasReedE);
		return hasReedE;
	}

	/**
	 * Get a receptor from library and add to model
	 * 
	 * @param parentSystem
	 * @param receptorName
	 * @return the subsystem of the receptor in the destiation model
	 */
	private ISubSystem addFromLibToModel(ISubSystem parentSystem, String receptorName) {
		SubSystemBlock recModel = (SubSystemBlock) libFinder.findByName(receptorName, false);
		ISubSystem ds = copySubSystem(libProject, recModel, getProject(), parentSystem);
		return ds;
	}

	private ISubSystem addReedLake(ISubSystem parent) throws PropertyVetoException {
		ISubSystem ss = addFromLibToModel(parent, "Aquatic");
		ss.setName("R");
		return ss;
	}

	private ISubSystem addOpenLake(ISubSystem parent) throws PropertyVetoException {
		ISubSystem ss = addFromLibToModel(parent, "Aquatic");
		ss.setName("W");
		return ss;

	}

	private ISubSystem addForest(SubSystemBlock parent) throws PropertyVetoException {
		ISubSystem ss = addFromLibToModel(parent, "Forest");
		ss.setName("F");
		return ss;

	}

	private ISubSystem addWetland(SubSystemBlock parent) throws PropertyVetoException {
		ISubSystem ss = addFromLibToModel(parent, "Wetland");
		ss.setName("M");
		return ss;

	}

	private ISubSystem addPastureland(SubSystemBlock parent) throws PropertyVetoException {
		ISubSystem ss = addFromLibToModel(parent, "Cropland");
		ss.setName("P");
		return ss;

	}

	private ISubSystem addCroplandI(SubSystemBlock parent) throws PropertyVetoException {
		ISubSystem ss = addFromLibToModel(parent, "Cropland");
		ss.setName("I");
		return ss;

	}

	private ISubSystem addCroplandN(SubSystemBlock parent) throws PropertyVetoException {
		ISubSystem ss = addFromLibToModel(parent, "Cropland");
		ss.setName("N");
		return ss;

	}

	private ISubSystem addCoast(String name) throws PropertyVetoException {
		ISubSystem parent = getProject().getDataModel().getRootSubSystem();
		ISubSystem ss = addFromLibToModel(parent, "Aquatic");
		ss.setName(name);
		return ss;

	}

	private SubSystemBlock createSubSystem(String name) {
		ISubSystem parent = getProject().getDataModel().getRootSubSystem();
		SubSystemBlock ss = dataObjectFactory.createSubSystem(name, parent);
		getProject().getDataModel().getBlockModel().add(ss);
		return ss;

	}

	private SubSystemBlock createSubSystem(String name, ISubSystem parent) {
		SubSystemBlock ss = new SubSystemBlock(name, parent);
		getProject().getDataModel().getBlockModel().add(ss);
		return ss;

	}

	protected void copyObject(EcolegoProject fromProject, EcolegoProject toProject, IDataObject object) {

		// EcolegoIndexList il =
		// fromProject.getDataModel().getIndexListModel().getByName(name);
		List<IDataObject> objects = new ArrayList<IDataObject>();
		objects.add(object);
		Transferable transferable = TransferManager.getInstance().createTransferable(fromProject, objects);
		ISubSystem targetSubSystem = toProject.getDataModel().getRootSubSystem();
		TransferManager.getInstance().transfer(toProject, targetSubSystem, transferable, TransferHandler.COPY);

	}

}
