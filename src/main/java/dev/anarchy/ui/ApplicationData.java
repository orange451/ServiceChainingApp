package dev.anarchy.ui;

import java.awt.Desktop;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.anarchy.common.DCollection;
import dev.anarchy.common.DCollectionMetadata;
import dev.anarchy.common.DFolder;
import dev.anarchy.common.DFolderElement;
import dev.anarchy.common.DServiceChain;
import dev.anarchy.common.util.RouteHelper;
import dev.anarchy.event.Event;
import dev.anarchy.translate.util.FileUtils;
import dev.anarchy.translate.util.JSONUtils;
import dev.anarchy.translate.util.ServiceChainHelper;
import javafx.scene.control.Alert.AlertType;

public class ApplicationData {
	@JsonIgnore
	public DCollection UNORGANIZED = new DCollection();
	
	@JsonProperty("Collections")
	private List<DCollection> collections = new ArrayList<>();
	
	@JsonIgnore
	private Map<DCollection, File> collectionFileMap = new HashMap<>();
	
	@JsonIgnore
	private Map<File, DCollection> fileCollectionMap = new HashMap<>();
	
	@JsonIgnore
	private Map<File, DServiceChain> fileServiceChainMap = new HashMap<>();

	@JsonIgnore
	private Event onCollectionAddedEvent = new Event();
	
	@JsonIgnore
	private Event onCollectionRemovedEvent = new Event();

	@JsonIgnore
	private static final String APPLICATION_NAME = "ServiceChainer";

	@JsonIgnore
	private static final String APPLICATION_FILENAME = "AppData.json";
	
	@JsonIgnore
	private static final String APPLICATION_METADATA = "metadata.json";
	
	@JsonIgnore
	private static final String EXTENSION_HANDLER_FILENAME = "ExtensionHandler.json";
	
	@JsonIgnore
	private static final String EXTENSION_HANDLER_FOLDER = "ExtensionHandlers";
	
	public ApplicationData() {
		UNORGANIZED.setDeletable(false);
		UNORGANIZED.setArchivable(false);
		UNORGANIZED.setName("Unorganized");
	}
	
	private void onLoad() {
		if ( UNORGANIZED != null && !this.collections.contains(UNORGANIZED) )
			this.addCollection(UNORGANIZED);
	}
	
	private String generateNewChildElementName(DFolder parent, String baseName) {
		int index = 0;
		String checkName = baseName;
		while(parent.getChild(checkName) != null) {
			index += 1;
			checkName = baseName + " (" + index + ")"; 
		}
		
		return checkName;
	}
	
	private String generateNewCollectionName(String baseName) {
		int index = 0;
		String checkName = baseName;
		while(getCollection(checkName) != null) {
			index += 1;
			checkName = baseName + " (" + index + ")"; 
		}
		
		return checkName;
	}

	@JsonIgnore
	public DServiceChain newServiceChain(DFolder parent) {
		return newServiceChain(parent, new DServiceChain().getName());
	}

	@JsonIgnore
	public DServiceChain newServiceChain(DFolder parent, String baseName) {
		DServiceChain chain = new DServiceChain();
		String newName = generateNewChildElementName(parent, baseName);
		
		chain.setName(newName);
		parent.addChild(chain);
		
		save();
		return chain;
	}

	@JsonIgnore
	public DFolder newFolder(DFolder parent) {
		return newFolder(parent, new DFolder().getName());
	}

	@JsonIgnore
	public DFolder newFolder(DFolder parent, String baseName) {
		DFolder folder = new DFolder();
		String newName = generateNewChildElementName(parent, baseName);
		
		folder.setName(newName);
		parent.addChild(folder);
		
		save();
		
		return folder;
	}

	@JsonIgnore
	public DCollection newCollection() {
		return newCollection("New Collection");
	}
	
	public DCollection newCollection(String baseName) {
		DCollection collection = new DCollection();
		
		int index = 0;
		String checkName = baseName;
		while(getCollection(checkName) != null) {
			index += 1;
			checkName = baseName + " (" + index + ")"; 
		}
		
		collection.setName(checkName);
		this.addCollection(collection);
		return collection;
	}
	
	@JsonIgnore
	public DCollection getCollection(String collectionName) {
		for (DCollection collection : collections) {
			if (collection.getName().equals(collectionName) )
				return collection;
		}
		
		return null;
	}

	@JsonIgnore
	public List<DCollection> getCollectionsUnmodifyable() {
		DCollection[] arr = new DCollection[this.collections.size()];
		for (int i = 0; i < collections.size(); i++) {
			arr[i] = collections.get(i);
		}
		return Arrays.asList(arr);
	}

	@JsonIgnore
	public void addCollection(DCollection collection) {
		if ( collections.add(collection) ) {
			onCollectionAddedEvent.fire(collection);
			save();
			
			collection.getOnNameChangeEvent().connect((args)->{
				String newName = args[0].toString();
				String newFileName = getFileName(newName);
				
				File file = collectionFileMap.get(collection);
				System.out.println("Attempting to rename " + collection + " with name: " + newName);
				if ( file != null ) {
					File newFile = renameFile(file, newFileName);
					
					fileCollectionMap.remove(file);
					fileCollectionMap.put(newFile, collection);
					collectionFileMap.put(collection, newFile);
				} else {
					ServiceChainerApp.get().alert(AlertType.ERROR, "Something went wrong renaming collection.\nCould not locate folder in system path.");
				}
				
				saveCollection(collection);
			});
		}
	}
	
	private File renameFile(File source, String newName) {
		try {
			File newFile = new File(source.getParentFile().getAbsoluteFile() + File.separator + newName);
			source.renameTo(newFile);
			
			return newFile;
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		return null;
	}

	@JsonIgnore
	public void removeCollection(DCollection collection) {
		// Remove collection internally
		if ( collections.remove(collection) ) {
			onCollectionRemovedEvent.fire(collection);
		}
		
		// Remove Collection file
		try {
			File file = collectionFileMap.remove(collection);
			if ( file != null && file.exists() ) {
				deleteDirectory(file);
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	@JsonIgnore
	private boolean deleteDirectory(File path) {
		System.out.println("Deleting: " + path.getAbsolutePath());
	    if (path.exists()) {
	        File[] files = path.listFiles();
	        for (int i = 0; i < files.length; i++) {
	            if (files[i].isDirectory()) {
	                deleteDirectory(files[i]);
	            } else {
	                files[i].delete();
	            }
	        }
	    }
	    return path.delete();
	}

	@JsonIgnore
	public Event getOnCollectionAddedEvent() {
		return this.onCollectionAddedEvent;
	}

	@JsonIgnore
	public Event getOnCollectionRemovedEvent() {
		return this.onCollectionRemovedEvent;
	}

	@JsonIgnore
	public static String getAppDataPath() {
		return System.getProperty("user.home") + File.separator + APPLICATION_NAME + File.separator;
	}

	@JsonIgnore
	public static String getAppDataFilePath() {
		return getAppDataPath() + APPLICATION_FILENAME;
	}

	@JsonIgnore
	public static String getAppDataFilePath(String append) {
		return getAppDataPath() + append;
	}

	@JsonIgnore
	public static ApplicationData load() {
		ApplicationData appData = new ApplicationData();
		
		// Get all potential collections
		File[] directories = new File(getAppDataPath()).listFiles(new FilenameFilter() {
		  @Override
		  public boolean accept(File current, String name) {
		    return new File(current, name).isDirectory();
		  }
		});
		
		// Legacy loader, so people dont lose their collections.
		if ( directories.length == 0 ) {
			ApplicationData data = loadLegacy();
			data.save();
			return data;
		}
		
		// Check all collections
		List<DCollection> newCollections = new ArrayList<>();
		if ( directories != null ) {
			for (File file : directories) {
				DCollection collection = new DCollection();
				ObjectMapper objectMapper = new ObjectMapper();
				
				// Load Metadata
				try {
					byte[] data = Files.readAllBytes(Paths.get(file.getAbsolutePath() + File.separator + APPLICATION_METADATA));
					String json = new String(data, StandardCharsets.UTF_8);
					System.out.println(json);
					DCollectionMetadata metadata = objectMapper.readValue(json, DCollectionMetadata.class);
					
					// Import Metadata
					loadMetadata(file, metadata, collection);
				} catch(Exception e) {
					e.printStackTrace();
				}

				// Finalize
				if ( collection.getName().equals("Unorganized") ) {
					collection.setDeletable(false);
					appData.UNORGANIZED = collection;
					newCollections.add(0, collection);
				} else {
					newCollections.add(collection);
				}
				
				appData.collectionFileMap.put(collection, file);
				appData.fileCollectionMap.put(file, collection);
						
				/*String appDataPath = file.getAbsolutePath() + File.separator + APPLICATION_FILENAME;
				System.out.println("Checking: " + appDataPath);
				File appDataFile = new File(appDataPath);
				if ( appDataFile.exists() ) {
					try {
						Path path = Paths.get(appDataPath);
						byte[] data = Files.readAllBytes(path);
						String json = new String(data, StandardCharsets.UTF_8);
						System.out.println("READ JSON: " + json);
						
						DCollection collection = objectMapper.readValue(json, DCollection.class);
						
						if ( collection.getName().equals("Unorganized") && !collection.isDeletable() ) {
							appData.UNORGANIZED = collection;
							newCollections.add(0, collection);
						} else {
							newCollections.add(collection);
						}
						
						appData.collectionFileMap.put(collection, file);
						appData.fileCollectionMap.put(file, collection);
					} catch(Exception e) {
						e.printStackTrace();
					}
				}*/
			}
		}
		
		// Add
		for (DCollection collection : newCollections) {
			appData.addCollection(collection);
		}
		
		appData.onLoad();
		return appData;
	}

	private static void loadMetadata(File collectionFile, DCollectionMetadata metadata, DFolder folder) {
		folder.setName(metadata.getName());
		
		for (DCollectionMetadata child : metadata.getChildren()) {
			DFolder subFolder = new DFolder();
			loadMetadata(collectionFile, child, subFolder);
			folder.addChild(subFolder);
		}
		
		for (String fileName : metadata.getExtensionHandlers()) {
			try {
				fileName = getFileName(fileName);
				String extensionFilePath = collectionFile.getAbsolutePath() + File.separator + EXTENSION_HANDLER_FOLDER + File.separator + fileName + ".json";
				
				Path path = Paths.get(extensionFilePath);
				byte[] data = Files.readAllBytes(path);
				String json = new String(data, StandardCharsets.UTF_8);
				DServiceChain serviceChain = new ObjectMapper().readValue(json, DServiceChain.class);
				folder.addChild(serviceChain);
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
	}

	@JsonIgnore
	@Deprecated
	public static ApplicationData loadLegacy() {
		ApplicationData appData;
		String json = null;
		try {
			System.out.println("LOAD LEGACY: " + ApplicationData.getAppDataFilePath());
			Path path = Paths.get(ApplicationData.getAppDataFilePath());
			System.out.println("LOAD2: " + path);
			byte[] data = Files.readAllBytes(path);
			json = new String(data, StandardCharsets.UTF_8);
			
			System.out.println("READ JSON: " + json);
			
			ObjectMapper objectMapper = new ObjectMapper();
			appData = objectMapper.readValue(json, ApplicationData.class);
		} catch (Exception e) {
			e.printStackTrace();
			appData = new ApplicationData();
		}
		
		for (DCollection collection : appData.getCollectionsUnmodifyable()) {
			if ( collection.getName().equals("Unorganized") && !collection.isDeletable() ) {
				appData.UNORGANIZED = collection;
				break;
			}
		}
		
		appData.onLoad();
		return appData;
	}

	@JsonIgnore
	public void save() {
		File appData = new File(getAppDataPath());
		if ( !appData.exists() )
			appData.mkdirs();
		
		for (DCollection collection : collections) {
			saveCollection(collection);
		}
	}

	protected void saveCollection(DCollection collection) {
		String collectionPath = getAppDataFilePath(getFileName(collection.getName()));
		String metadataPath = collectionPath + File.separator + APPLICATION_METADATA;
		String extensionsPath = collectionPath + File.separator + EXTENSION_HANDLER_FOLDER;
		List<DServiceChain> serviceChains = RouteHelper.getServiceChains(collection);
		Map<DServiceChain, String> serviceChainFileNames = new HashMap<>();
		
		File folder = new File(collectionPath);
		if ( !folder.exists() )
			folder.mkdirs();

		// Make sure the collection has a map to file
		collectionFileMap.put(collection, folder);
		
		// Output metadata
		try {
			DCollectionMetadata meta = fetchMeta(collection, serviceChainFileNames);
			writeObject(meta, metadataPath);
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		// Service Chains Folder
		File extensionFolder = new File(extensionsPath);
		if ( !extensionFolder.exists() )
			extensionFolder.mkdirs();
		
		// Output Service chains
		for (DServiceChain serviceChain : serviceChains) {
			try {
				String name = serviceChainFileNames.get(serviceChain);
				File file = writeObject(serviceChain, extensionsPath + File.separator + name + ".json");
				fileServiceChainMap.put(file, serviceChain);
				
				// TODO dont connect to this every time we save.... resource leak
				serviceChain.getOnNameChangeEvent().connect((args)->{
					File newFile = renameFile(file, getFileName(args[0].toString()) + ".json");
					fileServiceChainMap.remove(file);
					fileServiceChainMap.put(newFile, serviceChain);
				});
				
				// TODO connect to parent node and track deletion OF this service chain. We need to clean up the file
			} catch(Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	private File writeObject(Object object, String filepath) throws IOException {
		String json = serializeJSON(object);
		
		FileWriter fw = new FileWriter(filepath);
	    BufferedWriter writer = new BufferedWriter(fw);
	    writer.write(json);
	    writer.close();
	    
	    return new File(filepath);
	}

	private DCollectionMetadata fetchMeta(DFolder collection, Map<DServiceChain, String> serviceChainFileNames) {
		DCollectionMetadata meta = new DCollectionMetadata();
		meta.setName(collection.getName());
		
		for (DFolderElement element : collection.getChildrenUnmodifyable()) {
			if ( element instanceof DFolder ) {
				DCollectionMetadata child = fetchMeta((DFolder) element, serviceChainFileNames);
				meta.getChildren().add(child);
			}
			if ( element instanceof DServiceChain ) {
				String potentialFileName = getFileName(element.getName());
				String newFileName = potentialFileName;
				int index = 1;
				while ( serviceChainFileNames.values().contains(newFileName) ) {
					newFileName = potentialFileName + "." + index;
				}
				
				meta.getExtensionHandlers().add(newFileName);
				serviceChainFileNames.put((DServiceChain) element, newFileName);
			}
		}
		
		return meta;
	}

	@JsonIgnore
	public static String getFileName(String name) {
		return name.replaceAll("[\\\\/:*?\"<>|]", "");
	}

	@JsonIgnore
	@Deprecated
	public void saveLegacy() {
		String json = this.serializeJSON(this);
		
		try {
			String path = getAppDataFilePath();
			File file = new File(path);
			if ( !file.getParentFile().exists() )
				file.getParentFile().mkdirs();
			
		    BufferedWriter writer = new BufferedWriter(new FileWriter(path));
		    writer.write(json);
		    writer.close();
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	@JsonIgnore
	private String serializeJSON(Object object) {
		String json = JSONUtils.objectToJSON(object);
		if ( StringUtils.isEmpty(json) )
			json = "{}";
		
		return json;
	}
	
	/**
	 * Get the collection that a generic element exists within.
	 */
	public DCollection getCollection(DFolderElement element) {
		if ( element instanceof DCollection )
			return (DCollection)element;
		
		for (DCollection collection : getCollectionsUnmodifyable()) {
			DFolder folder = getParentRecursive(element, collection);
			if ( folder != null )
				return collection;
		}
		
		return null;
	}
	
	/**
	 * Get the parent Folder/Collection to a generic element
	 * @param element
	 * @return
	 */
	@JsonIgnore
	public DFolder getParent(DFolderElement element) {
		for (DCollection collection : getCollectionsUnmodifyable()) {
			DFolder folder = getParentRecursive(element, collection);
			if ( folder != null )
				return folder;
		}
		
		return null;
	}

	@JsonIgnore
	private DFolder getParentRecursive(DFolderElement check, DFolder parent) {
		for (DFolderElement child : parent.getChildrenUnmodifyable()) {
			if ( child == check ) {
				return parent;
			}
			
			if ( child instanceof DFolder ) {
				DFolder match = getParentRecursive(check, (DFolder) child);
				if ( match != null )
					return match;
			}
		}
		
		return null;
	}

	@JsonIgnore
	public DFolder duplicate(DCollection internal) {
		DFolder newCollection = internal.clone();
		newCollection.setName(generateNewCollectionName(internal.getName()));
		newCollection.setDeletable(true);
		newCollection.setArchivable(true);
		this.addCollection((DCollection) newCollection);
		
		this.save();
		return newCollection;
	}

	@JsonIgnore
	public DFolder duplicate(DFolder internal) {
		if ( internal instanceof DCollection )
			return duplicate((DCollection)internal);
		
		DFolder parent = getParent(internal);
		DFolder newFolder = internal.clone();
		newFolder.setName(this.generateNewChildElementName(parent, newFolder.getName()));
		parent.addChild(newFolder);
		
		this.save();
		return newFolder;
	}

	@JsonIgnore
	public DServiceChain duplicate(DServiceChain internal) {
		DFolder parent = getParent(internal);
		DServiceChain newObject = internal.clone();
		newObject.setName(this.generateNewChildElementName(parent, newObject.getName()));
		parent.addChild(newObject);
		
		this.save();
		return newObject;
	}

	@SuppressWarnings("unchecked")
	@JsonIgnore
	protected void importCollection(File selectedFile, DFolder parentFolder) {
		if (selectedFile != null) {
			String fileName = FileUtils.getFileNameFromPathWithoutExtension(selectedFile.getAbsolutePath());

			ObjectMapper objectMapper = new ObjectMapper();
			
			try {
				// Read file contents
				Path path = selectedFile.toPath();
				byte[] data = Files.readAllBytes(path);
				String json = new String(data, StandardCharsets.UTF_8);
				json = json.trim();
				
				// Ugly fix due to typo early on. Commenting out will break OLD service chain handlerid names. New ones will not be affected.
				json = dirtyHandlerFix(json);
				
				// Convert to map
				System.out.println("Attempting to import: " + fileName);
				Map<String, Object> map = objectMapper.readValue(json, Map.class);
				
				// Locate extensionHandler
				Map<String, Object> extensionHandlerParent = locateKeyInMap(map, "ExtensionHandler");
				if ( extensionHandlerParent == null ) {
					extensionHandlerParent = generateFakeExtensionHandler(map);
					System.out.println("Could not find ExtensionHandler. Using whole payload.");
				}
				
				// Create new collection
				extensionHandlerParent.put("_IsCollection", true); // Mark as collection, so there's no confusion how this gets marshaled
				DCollection newCollection = objectMapper.convertValue(extensionHandlerParent, DCollection.class);
				newCollection.setName(fileName);
				System.out.println("Created new Collection: " + newCollection);
				
				// Make sure it has kids
				if ( newCollection.getChildrenUnmodifyable().size() <= 0 ) {
					ServiceChainerApp.get().alert(AlertType.ERROR, "Could not import collection. Check file contents and try again.");
					return;
				}
				
				// Fix missing Metadata
				for (DFolderElement child : newCollection.getChildrenUnmodifyable()) {
					System.out.println("Checking Metadata: " + child);
					ServiceChainHelper.fixServiceChain((DServiceChain)child);
				}
				
				// If no parent is specified, put it as a new collection
				if ( parentFolder == null ) {
					ServiceChainerApp.get().getData().addCollection(newCollection);
				} else {
					
					// If there's more than 1 child being added, wrap it in a folder
					if ( newCollection.getChildrenUnmodifyable().size() > 1 && parentFolder.getChildrenUnmodifyable().size() > 0 ) {
						DFolder folder = newFolder(parentFolder, newCollection.getName());
						parentFolder = folder;
					}
					
					// If parent is specified, empty its children in to the parent
					for (DFolderElement element : newCollection.getChildrenUnmodifyable()) {
						String elementName = fileName;
						if ( element instanceof DServiceChain ) {
							String name = ((DServiceChain)element).getName();
							elementName = name;
							
							if ( StringUtils.isEmpty(name) ) {
								String handlerId = ((DServiceChain)element).getHandlerId();
								elementName = StringUtils.isEmpty(handlerId) ? fileName : handlerId;
							}
						}
						
						System.out.println("Adding element: " + element + " to parent folder: " + parentFolder);
						String name = this.generateNewChildElementName(parentFolder, elementName);
						((DServiceChain)element).setName(name);
						
						parentFolder.addChild(element);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		DCollection collection = this.getCollection(parentFolder);
		if ( collection != null ) {
			saveCollection(collection);
		}
	}

	private Map<String, Object> generateFakeExtensionHandler(Map<String, Object> map) {
		Map<String, Object> extension = new HashMap<>();
		List<Object> handlers = new ArrayList<>();
		handlers.add(map);
		
		extension.put("ExtensionHandler", handlers);
		return extension;
	}

	@Deprecated
	private String dirtyHandlerFix(String json) {
		return json.replace("\"ExtensionhandlerId\"", "\"ExtensionHandlerId\"");
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> locateKeyInMap(Map<String, Object> map, String keyName) {
		for (Entry<String, Object> set : map.entrySet()) {
			Object value = set.getValue();
			
			if ( StringUtils.equals(set.getKey(), keyName) ) {
				return map;
			}

			if ( value instanceof Map ) {
				Object newValue = locateKeyInMap((Map<String, Object>) value, keyName);
				if ( newValue != null )
					return (Map<String, Object>) newValue;
			}
			
			if ( value instanceof List ) {
				List<Object> list = (List<Object>)value;
				for (Object object : list) {
					if ( object instanceof Map ) {
						Object newValue = locateKeyInMap((Map<String, Object>) object, keyName);
						if ( newValue != null )
							return (Map<String, Object>) newValue;
					}
				}
			}
		}
		
		return null;
	}

	@JsonIgnore
	public void openExplorer(DCollection internal) {
        Desktop desktop = Desktop.getDesktop();
        File dirToOpen = null;
        try {
            dirToOpen = collectionFileMap.get(internal);
            if ( dirToOpen == null ) {
            	ServiceChainerApp.get().alert(AlertType.ERROR, "Error opening directory. Something went wrong.");
            	return;
            }
            desktop.open(dirToOpen);
            System.out.println(dirToOpen);
        } catch (IllegalArgumentException | IOException e) {
        	ServiceChainerApp.get().alert(AlertType.ERROR, "Error opening directory.\n" + e.getMessage());
        	e.printStackTrace();
        }
	}
}
