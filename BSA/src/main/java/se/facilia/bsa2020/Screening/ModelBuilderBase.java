package se.facilia.bsa2020.Screening;

import java.awt.datatransfer.Transferable;
import java.beans.PropertyVetoException;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;

import com.google.common.collect.Sets;

import se.facilia.ecolego.domain.EntryDataObject;
import se.facilia.ecolego.domain.GroupSubSystem;
import se.facilia.ecolego.domain.block.IBlock;
import se.facilia.ecolego.domain.datatransfer.TransferManager;
import se.facilia.ecolego.domain.model.DataModel;
import se.facilia.ecolego.domain.model.DataObjectFactory;
import se.facilia.ecolego.domain.model.DataObjectFinder;
import se.facilia.ecolego.domain.model.EcolegoProject;
import se.facilia.ecolego.main.Initializer;
import se.facilia.ecolego.presentation.model.IAutoAddFilter;
import se.facilia.ecolego.presentation.model.PresentationModel;
import se.facilia.ecolego.presentation.model.MatrixTreeConnectionModel.CreationMode;
import se.facilia.ecolego.ui.workbench.views.matrix.MatrixCellInfo;
import se.facilia.facilialib.domain.common.IDataObject;
import se.facilia.facilialib.domain.common.IMaterial;
import se.facilia.facilialib.domain.common.ISubSystem;
import se.facilia.facilialib.domain.workspace.Workspace;

public class ModelBuilderBase {

	
	private static DataObjectFinder finder;
	private String projectName;
	private File workDir;
	private EcolegoProject project;
	

	public ModelBuilderBase(File workDir, String projectName, boolean createOverwrite) throws Exception {		
		this.projectName = projectName;
		this.workDir = workDir;
		
		Initializer.init();
		
		//File f = new File(this.workDir, projectName + ".eco");
		
		project=openProject(workDir, projectName, createOverwrite);
		//project = createProject(this.workDir, projectName);
	}
	public EcolegoProject getProject() {
		return project;
	}
	
	protected EcolegoProject loadProject(File dir, String name) throws IOException {
		EcolegoProject p = (EcolegoProject) Workspace.getWorkspace().openProject(new File(dir, name + ".eco"));
		return p;
	}
	
	protected void save() throws IOException {
		Workspace.getWorkspace().saveProject(projectName);

	}
	protected void addNewMaterials(EcolegoProject fromProject, EcolegoProject toProject) {
		copyNewMaterials(fromProject, toProject, toProject.getDataModel().getRootSubSystem());

	}
	/**
	 * @return a list of materials that are available in the given project but
	 *         do not exist in the targetProject.
	 */
	private List<IMaterial> getNewMaterials(EcolegoProject project, EcolegoProject targetProject) {
		List<IMaterial> materials = new ArrayList<IMaterial>();

		for (IMaterial m : project.getDataModel().getMaterialModel()) {
			if (targetProject.getDataModel().getMaterialModel().getByName(m.getName()) == null) {
				materials.add(m);
			}
		}

		return materials;
	}
	protected void copyNewMaterials(EcolegoProject fromProject, EcolegoProject toProject, Object targetObject) {
		// Copy new materials
		List<IMaterial> newMaterials = getNewMaterials(fromProject, toProject);
		Transferable transferable = null;
		if (newMaterials.size() > 0) {
			transferable = TransferManager.getInstance().createTransferable(fromProject, newMaterials);

			Object targetSubSystem = targetObject;
			if (targetSubSystem instanceof MatrixCellInfo) {
				targetSubSystem = ((MatrixCellInfo) targetObject).getSubSystem();
			}
			// else if( targetSubSystem instanceof GraphInfo ) {
			// String subId =
			// ((GraphInfo)targetObject).getTargetSubSystem().getId();
			//
			// targetSubSystem =
			// targetProject.getDataModel().getFinder().getByGUID( GUID.create(
			// subId ));
			// }
			TransferManager.getInstance().transfer(toProject, targetSubSystem, transferable, TransferHandler.COPY);
		}
	}

	protected ISubSystem copySubSystem(EcolegoProject sourceProject, ISubSystem sourceSubSystem, EcolegoProject targetProject, Object target) {
		ISubSystem copied = null;

		boolean wasEnabled = targetProject.getDataModel().isValidationEnabled();

		try {
			targetProject.getDataModel().setValidationEnabled(false);

			Transferable transferable;
			// Copy sub-system
			transferable = TransferManager.getInstance().createTransferable(sourceProject, Arrays.asList(sourceSubSystem));
			copied = (ISubSystem) TransferManager.getInstance().transfer(targetProject, target, transferable, TransferHandler.COPY).get(0);

			// Copy blocks outside
			Set<EntryDataObject<?>> dependencies = Sets.newIdentityHashSet();

			List<IBlock> objects = TransferManager.getInstance().findBlocks(Arrays.asList((IDataObject) sourceSubSystem));
			DataObjectFinder sourceFinder = sourceProject.getDataModel().getFinder();
			DataObjectFinder targetFinder = targetProject.getDataModel().getFinder();

			for (IDataObject d : objects) {
				if (d instanceof EntryDataObject<?>) {
					addRootDependenciesRecursively(dependencies, sourceFinder, targetFinder, d);
				}
			}

			if (dependencies.size() > 0) {
				try {
					TransferManager.getInstance().setCreationMode(TransferManager.DONT_CREATE);
					// for( IDataObject d: dependencies ) {

					Map<ISubSystem, List<EntryDataObject<?>>> byGroup = dependencies.stream().collect(Collectors.groupingBy(e -> e.getSubSystem()));

					for (Entry<ISubSystem, List<EntryDataObject<?>>> entry : byGroup.entrySet()) {
						ISubSystem parent = assertGroupExists(targetProject.getDataModel(), entry.getKey());

						transferable = TransferManager.getInstance().createTransferable(sourceProject, entry.getValue());

						TransferManager.getInstance().transfer(targetProject, parent, transferable, TransferHandler.COPY);
					}
					// }
					// TransferManager.getInstance().setCreationMode(
					// TransferManager.DONT_CREATE );
					// for( IDataObject d: dependencies ) {
					// transferable =
					// TransferManager.getInstance().createTransferable(sourceProject,
					// Arrays.asList( d ) );
					//
					// TransferManager.getInstance().transfer( targetProject,
					// root, transferable, TransferHandler.COPY );
					// }
				} finally {
					TransferManager.getInstance().setCreationMode(TransferManager.CREATE);
				}
			}

		} finally {
			targetProject.getDataModel().setValidationEnabled(wasEnabled);
		}

		return copied;
	}

	/**
	 * Adds all objects in the root sub-system to which there are dependencies
	 * from the given object d
	 */
	protected void addRootDependenciesRecursively(Set<EntryDataObject<?>> dependencies, DataObjectFinder sourceFinder, DataObjectFinder targetFinder, IDataObject d) {
		for (EntryDataObject<?> edo : sourceFinder.getObjectsReferredBy((EntryDataObject<?>) d)) {
			String id = edo.getId();
			if (id.indexOf('.') == -1 && targetFinder.getById(id) == null) {
				dependencies.add(edo);

				addRootDependenciesRecursively(dependencies, sourceFinder, targetFinder, edo);
			}
		}
	}

	/**
	 * Makes sure that there is a group in the dataModel with the same name and
	 * path as the given group
	 */
	private ISubSystem assertGroupExists(DataModel dataModel, ISubSystem group) {
		if (group.isRoot()) {
			return dataModel.getRootSubSystem();
		} else {
			String id = group.getId();

			IDataObject o = dataModel.getFinder().getById(id);

			if (o instanceof GroupSubSystem) {
				return (ISubSystem) o;
			} else {
				ISubSystem parent = assertGroupExists(dataModel, group.getSubSystem());
				ISubSystem newGroup;
				if (o == null) {
					newGroup = DataObjectFactory.copy(group);
				} else {
					newGroup = dataModel.getFactory().createGroupSubSystem(group.getName(), parent);
				}

				try {
					newGroup.setSubSystem(parent);
				} catch (PropertyVetoException ignored) {
					ignored.printStackTrace();
				}

				PresentationModel targetModel = dataModel.getParent().getPresentationModel();
				CreationMode oldMode = targetModel.getTreeConnectionModel().getCreationMode();
				IAutoAddFilter graphMode = targetModel.getTreeConnectionModel().getAutoAddFilter();
				try {
					// targetModel.getGraph().setAutoAddFilter( object->false );
					targetModel.getTreeConnectionModel().setCreationMode(CreationMode.CREATE_NOTHING);

					dataModel.getBlockModel().add((IBlock) newGroup);
				} finally {
					targetModel.getTreeConnectionModel().setCreationMode(oldMode);
					// targetModel.getGraph().setAutoAddFilter( graphMode );
				}
				return newGroup;
			}
		}
	}


	private static EcolegoProject openProject(File dir, String name, boolean createOrOverwrite) throws IOException, InvocationTargetException, InterruptedException {
		File f = new File(dir, name + ".eco");
		EcolegoProject p = null;
		if (f.exists()) {
			if (createOrOverwrite) {
				f.delete();
				p = (EcolegoProject) Workspace.getWorkspace().createProject(name);
			} 
			p=(EcolegoProject) Workspace.getWorkspace().openProject(f);
		} else {
		
				p = (EcolegoProject) Workspace.getWorkspace().createProject(name);
			
				SwingUtilities.invokeAndWait(new Runnable() {
					
					@Override
					public void run() {
						try {
							Workspace.getWorkspace().saveProjectAs(name, new File(dir, name + ".eco"));
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						
					}
				});
				
			
		} 
		
		if (p!= null) {
			finder = p.getDataModel().getFinder();
		}
		

		return p;
	}

	protected static EcolegoProject createProject(File workDir, String name) throws IOException {
		File f = new File(workDir, name + ".eco");
		EcolegoProject p = null;
		if (!f.exists()) {
			p = (EcolegoProject) Workspace.getWorkspace().createProject(name);
			Workspace.getWorkspace().saveProjectAs(name, new File(workDir, name + ".eco"));
		} else {
			throw new IOException("Project already exists!");
		}
		finder = p.getDataModel().getFinder();
		return p;
	}


	public static DataObjectFinder getFinder() {
		return finder;
	}



}
