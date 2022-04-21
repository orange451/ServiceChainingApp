package dev.anarchy.ui.control.workspace.servicechain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import dev.anarchy.common.DConditionElement;
import dev.anarchy.common.DRouteElement;
import dev.anarchy.common.DRouteElementI;
import dev.anarchy.common.DServiceChain;
import dev.anarchy.common.DServiceDefinition;
import dev.anarchy.common.condition.ConditionMeta;
import dev.anarchy.common.util.RouteHelper;
import dev.anarchy.translate.util.ServiceChainHelper;
import dev.anarchy.ui.ServiceChainerApp;
import dev.anarchy.ui.control.workspace.GraphObject;
import dev.anarchy.ui.control.workspace.GraphObjectCondition;
import dev.anarchy.ui.util.ColorHelper;
import dev.anarchy.ui.util.IconHelper;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.CubicCurve;
import javafx.scene.shape.StrokeLineCap;

public class ServiceChainEditor extends BorderPane {

	private Pane editPane;
	
	private ScrollPane scroll;

	private GraphObject selectedNode;

	private List<CubicCurve> curves = new ArrayList<>();

	private List<GraphObject> nodes = new ArrayList<>();
	
	private DServiceChain internal;
	
	private boolean ignoreChangeEvents;

	public ServiceChainEditor(DServiceChain oldServiceChain) {
		ignoreChangeEvents = true;
		
		DServiceChain internal = oldServiceChain.clone();
		this.internal = internal;
		
		BorderPane topBar = new BorderPane();
		topBar.getStyleClass().add("Service-Chain-Editor-Topbar");
		topBar.setPadding(new Insets(8, 8, 8, 8));
		topBar.prefWidthProperty().bind(this.widthProperty());
		this.setTop(topBar);

		// Left top bar
		{
			HBox buttons = new HBox();
			buttons.setSpacing(6);
			
			{
				Button button = new Button("New Service Definition");
				button.setOnAction((event) -> {
					DServiceDefinition sDef = new DServiceDefinition();
					sDef.setExtensionHandlerRouteId("Service Definition");
					sDef.setColor(ServiceChainHelper.getDefaultServiceDefinitionColor());
					sDef.setSize(220, 60);
					double x = round(ServiceChainHelper.getDefaultServiceDefinitionX());
					double y = round(ServiceChainHelper.getDefaultServiceDefinitionY());
					sDef.setPosition(x, y);
					internal.addRoute(sDef);
				});
				buttons.getChildren().add(button);
			}
			
			{
				Button button = new Button("New Condition");
				//button.setDisable(true);
				button.setOnAction((event) -> {
					newCondition();
				});
				buttons.getChildren().add(button);
			}
			
			topBar.setLeft(buttons);
		}
		
		// Center top bar
		{
			Button button = new Button("Save");
			button.setPrefWidth(200);
			button.getStyleClass().add("info");
			
			button.setOnAction((event)->{
				ServiceChainerApp.get().saveCurrent();
			});
			
			Platform.runLater(() -> {
				SimpleBooleanProperty modifiedProperty = ServiceChainerApp.get().getWorkspace().getModifiedStatusProperty(oldServiceChain);
				button.visibleProperty().bind(modifiedProperty);
			});
			
			topBar.setCenter(button);
		}

		// Right top bar
		{
			HBox buttons2 = new HBox();
			buttons2.setSpacing(6);
			
			// Center
			{
				Button edit = new Button("x");
				edit.setOnMouseClicked((event)->{
					centerView();
				});
				buttons2.getChildren().add(edit);
			}
			
			// Edit
			{
				Button edit = new Button("", IconHelper.GEAR.create());
				edit.setOnMouseClicked((event)->{
					new ServiceChainConfigurator((DServiceChain) internal);
				});
				buttons2.getChildren().add(edit);
			}
			
			// Test
			{
				Button play = new Button("", IconHelper.PLAY.create(Color.WHITE));
				play.getStyleClass().add("primary");
				play.setOnMouseClicked((event)->{
					new ServiceChainRunner(internal).show();
				});
				buttons2.getChildren().add(play);
			}
			
			topBar.setRight(buttons2);
		}
		
		DropShadow dropShadow = new DropShadow();
		dropShadow.setRadius(5.0);
		dropShadow.setOffsetX(0.0);
		dropShadow.setOffsetY(1.0);
		dropShadow.setColor(Color.color(0.4, 0.5, 0.5));
		topBar.setEffect(dropShadow);

		scroll = new ScrollPane();
		scroll.setStyle("-fx-background: transparent; -fx-border-color: transparent; -fx-background-color:transparent;");
		scroll.setPadding(Insets.EMPTY);
		scroll.setBorder(Border.EMPTY);
		scroll.setHvalue(0.5);
		scroll.setVvalue(0.5);
		this.setCenter(scroll);

		this.editPane = new Pane();
		this.editPane.setPrefSize(4096, 4096);
		this.editPane.getStyleClass().add("Diagram-Editor");
		scroll.setContent(this.editPane);

		this.editPane.setOnMousePressed((event) -> {
			setSelectedNode(null);
		});

		// oldServiceChain rename
		oldServiceChain.getOnNameChangeEvent().connect((args)->{
			internal.setName(args[0].toString());
		});
		
		// Create Entry node
		createEntryPointNode(internal);

		// Create new Graph Object when new route is added
		internal.getOnRouteAddedEvent().connect((args) -> {
			newRouteElementNode(internal, (DRouteElement) args[0]);
		});
		
		// Create graph objects for all routes already in
		for (DRouteElementI element : internal.getRoutesUnmodifyable())
			newRouteElementNode(internal, element);

		// Remove graph object when route is removed
		internal.getOnRouteRemovedEvent().connect((args) -> {
			removeRouteElementNode(internal, (DRouteElement) args[0]);
		});
		
		// Create initial condition graph objects (Because conditions aren't in the route???? They're INSIDE SERVICE DEFINITIONS???? CANCER)
		extractConditionNodes();
		
		// Initial link for all graph objects
		new Thread(()->{
			try {Thread.sleep(10);} catch (InterruptedException e) {}
			Platform.runLater(()->{
				connectNodes();
				centerView();
				//postFixNodes();
				ignoreChangeEvents = false;
			});
		}).start();
	}

	private DConditionElement newCondition() {
		DConditionElement condition = new DConditionElement();
		condition.setName("Condition");
		condition.setColor(ServiceChainHelper.getDefaultConditionColor());
		condition.setSize(100, 100);
		double x = round(ServiceChainHelper.getDefaultServiceDefinitionX());
		double y = round(ServiceChainHelper.getDefaultServiceDefinitionY());
		condition.setPosition(x, y);
		internal.addRoute(condition);
		return condition;
	}

	private double getViewportValue(double x, double viewportLength, double totalLength, double ratio) {
		return (x - ratio * viewportLength) / (totalLength - viewportLength);
	}
	
	private void centerView() {
		// Get AABB of all nodes
		double minX = Math.min(Integer.MAX_VALUE, internal.getX());
		double minY = Math.min(Integer.MAX_VALUE, internal.getY());
		double maxX = Math.max(Integer.MIN_VALUE, internal.getX()+internal.getWidth());
		double maxY = Math.max(Integer.MIN_VALUE, internal.getY()+internal.getHeight());
		for (DRouteElementI element : internal.getRoutesUnmodifyable()) {
			minX = Math.min(minX, element.getX());
			minY = Math.min(minY, element.getY());
			maxX = Math.max(maxX, element.getX()+element.getWidth());
			maxY = Math.max(maxY, element.getY()+element.getHeight());
		}
		
		// Center position of node AABB
		double xx = (maxX+minX) / 2d;
		double yy = (maxY+minY) / 2d;
		
		// Center view over nodes
		scroll.setHvalue(getViewportValue(xx, scroll.getViewportBounds().getWidth(), editPane.getPrefWidth(), 0.5d));
		scroll.setVvalue(getViewportValue(yy, scroll.getViewportBounds().getHeight(), editPane.getPrefHeight(), 0.5d));
	}

	private void createEntryPointNode(DServiceChain internal) {
		GraphObject entryNode = newRouteElementNode(internal, internal);
		if (internal.getX() == 0 && internal.getY() == 0) {
			internal.setSize(140, 80);
			double x = round(editPane.getPrefWidth() / 2) - round(entryNode.getPrefWidth() / 2);
			double y = round(editPane.getPrefWidth() / 2 * 0.9125) - round(entryNode.getPrefHeight() / 2);
			internal.setPosition(x, y);
		}
	}

	public void removeNode(GraphObject object) {
		this.editPane.getChildren().remove(object);
		this.nodes.remove(object);
		this.connectNodes();
	}

	private DRouteElementI getRouteElement(String destinationId) {
		if ( "ON_EVENT".equals(destinationId) ) {
			return this.internal;
		}
		
		for (DRouteElementI route : internal.getRoutesUnmodifyable()) {
			if ( route.getDestinationId().equalsIgnoreCase(destinationId))
				return route;
		}
		return null;
	}

	protected void clearCurves() {
		for (Node node : this.editPane.getChildrenUnmodifiable()) {
			if (node instanceof CubicCurve)
				this.editPane.getChildren().remove(node);
		}
		
		for (Node node : this.curves) {
			this.editPane.getChildren().remove(node);
		}

		curves.clear();
	}

	public void connectNodes() {
		clearCurves();

		System.err.println("Reconnecting nodes");
		
		// Make sure condition nodes are attached
		for (GraphObject node : nodes) {
			if ( node.getRouteElement() instanceof DConditionElement ) {
				
			}
		}
		
		for (GraphObject node : nodes) {
			for (GraphObject conTo : nodes) {
				if (conTo == node)
					continue;

				DRouteElementI fromElement = node.getRouteElement();
				DRouteElementI toElement = conTo.getRouteElement();
				
				String source = toElement.getSourceId(); // From node potential
				String dest = fromElement.getDestinationId(); // From Node real
				
				if (source != null && source.equals(dest)) {
					toElement = getToElement(node, fromElement, conTo, toElement);
					conTo = getGraphObjectFromRoute(toElement);
					if ( conTo == null )
						continue;
					
					// Draw line
					connectNode(node, conTo);
					
					/*String entryCondition = null;
					ConditionMeta fromMeta = null;
					if ( fromElement instanceof DConditionElement ) {
						entryCondition = ((DConditionElement)fromElement).getCondition();
						fromMeta = ((DConditionElement)fromElement).getConditionMeta();
					}*/
					
					/*if ( toElement instanceof DServiceDefinition ) {
						if ( !StringUtils.isEmpty(entryCondition) ) {
							((DServiceDefinition)toElement).setCondition(entryCondition);
							((DServiceDefinition)toElement).setConditionMeta(fromMeta);
						} else {
							((DServiceDefinition)toElement).setCondition(null);
							((DServiceDefinition)toElement).setConditionMeta(null);
						}
					}*/
				}
			}
		}
	}
	
	private DRouteElementI getToElement(GraphObject fromNode, DRouteElementI fromElement, GraphObject toNode, DRouteElementI originalToElement) {
		/*System.err.println("\t - " + fromElement + "("+fromNode+") => " + originalToElement + "("+toNode+")");
		
		// If we're connecting to a service def, try to find the corresponding condition node
		if ( originalToElement instanceof DServiceDefinition ) {
			if ( !(fromElement instanceof DConditionElement) ) {
				ConditionMeta meta = ((DServiceDefinition)originalToElement).getConditionMeta();
				if ( meta != null ) {
					for (GraphObject node : nodes) {
						if ( node.getRouteElement() instanceof DConditionElement ) {
							if (meta.equals(((DConditionElement)node.getRouteElement()).getConditionMeta())) {
								return node.getRouteElement();
							}
						}
					}
				}
			}
		}*/
		
		return originalToElement;
	}

	private void connectNode(GraphObject from, GraphObject to) {

		CubicCurve curve = new CubicCurve();
		curve.setStroke(Color.FORESTGREEN);
		curve.setStrokeWidth(4);
		curve.setStrokeLineCap(StrokeLineCap.ROUND);
		curve.setFill(Color.TRANSPARENT);

		updateCurve(curve, from, to);

		from.widthProperty().addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> arg0, Number arg1, Number arg2) {
				updateCurve(curve, from, to);
			}
		});

		from.heightProperty().addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> arg0, Number arg1, Number arg2) {
				updateCurve(curve, from, to);
			}
		});

		from.translateXProperty().addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> arg0, Number arg1, Number arg2) {
				updateCurve(curve, from, to);
			}
		});

		from.translateYProperty().addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> arg0, Number arg1, Number arg2) {
				updateCurve(curve, from, to);
			}
		});
		
		to.widthProperty().addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> arg0, Number arg1, Number arg2) {
				updateCurve(curve, from, to);
			}
		});

		to.heightProperty().addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> arg0, Number arg1, Number arg2) {
				updateCurve(curve, from, to);
			}
		});

		to.translateXProperty().addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> arg0, Number arg1, Number arg2) {
				updateCurve(curve, from, to);
			}
		});

		to.translateYProperty().addListener(new ChangeListener<Number>() {
			@Override
			public void changed(ObservableValue<? extends Number> arg0, Number arg1, Number arg2) {
				updateCurve(curve, from, to);
			}
		});

		this.editPane.getChildren().add(0, curve);
		curves.add(curve);

		from.getLinkNode().update();
	}

	private void updateCurve(CubicCurve curve, GraphObject from, GraphObject to) {
		double x1 = from.getTranslateX() + from.getWidth() / 2;
		double y1 = from.getTranslateY() + from.getHeight() / 2;
		double x2 = to.getTranslateX() + to.getWidth() / 2;
		double y2 = to.getTranslateY() + to.getHeight() / 2;

		curve.setStartX(x1);
		curve.setStartY(y1);
		curve.setControlX1(x1);
		curve.setControlY1(y2);
		curve.setControlX2(x2);
		curve.setControlY2(y1);
		curve.setEndX(x2);
		curve.setEndY(y2);
	}

	private void removeRouteElementNode(DServiceChain parent, DRouteElement routeElement) {
		List<Node> toRemove = new ArrayList<>();
		
		for (Node node : this.nodes) {
			if (node instanceof GraphObject) {
				if (routeElement.equals(((GraphObject) node).getRouteElement())) {
					this.editPane.getChildren().remove(node);
					toRemove.add(node);
				}
			}
		}
		
		for (Node node : toRemove)
			nodes.remove(node);
		
		Platform.runLater(()->{
			connectNodes();
		});
	}
	
	private void extractConditionNodes() {
		double SPACING = 40;
		List<DRouteElementI> routes = internal.getRoutesUnmodifyable();
		DRouteElementI currentElement = internal;
		DRouteElementI prevElement = currentElement;
		double yOff = 0;
		while(currentElement != null) {
			currentElement = RouteHelper.getLinkedTo(routes, currentElement);
			if ( currentElement == null )
				break;
			
			System.err.println("Setting position of: " + currentElement + " to " + currentElement.getX() + ", " + currentElement.getY());
			
			if (currentElement instanceof DServiceDefinition) {
				if (!StringUtils.isEmpty(((DServiceDefinition)currentElement).getCondition())) {
					DConditionElement condition = newCondition();
					condition.setPosition(currentElement.getX(), currentElement.getY());
					condition.setCondition(((DServiceDefinition) currentElement).getCondition());
					
					
					RouteHelper.linkRoutes(internal.getRoutesUnmodifyable(), prevElement, condition);
					RouteHelper.linkRoutes(internal.getRoutesUnmodifyable(), condition, (DRouteElement) currentElement);
					
					((DServiceDefinition) currentElement).setCondition(null);
					yOff += condition.getHeight() + SPACING;
				}
			}

			currentElement.setPosition(currentElement.getX(), currentElement.getY() + yOff);
			prevElement = currentElement;
		}
	}
	
	/*private void postFixNodes() {
		// Properly re-link service definition condition nodes. SUPER hacky
		for (DRouteElementI element : internal.getRoutesUnmodifyable()) {
			if (element instanceof DServiceDefinition) {
				for (GraphObject node : nodes) {
					DRouteElementI element2 = node.getRouteElement();
					if ( element2 instanceof DConditionElement ) {
						if ( element.getSourceId().equals(element2.getDestinationId() ) ) {
							((DServiceDefinition) element).setSourceId(element2.getSourceId());
							System.err.println("OVERRIDING SOURCEID OF: " + element + " TO: " + element2.getSourceId());
						}
					}
				}
			}
		}
	}*/

	private GraphObjectCondition newConditionNode_temp(DServiceDefinition source) {
		DConditionElement condition = new DConditionElement();
		
		// Setup new condition element
		processConditionNode_temp(source, condition);
		
		// Update on change
		condition.getOnChangedEvent().connect((args)->{
			condition.getConditionMeta().setPosition(condition.getX(), condition.getY());
			condition.getConditionMeta().setSize(condition.getWidth(), condition.getHeight());
			condition.getConditionMeta().setColor(condition.getColor());
			condition.getConditionMeta().setName(condition.getName());
		});
		
		// Create graph object
		GraphObjectCondition g = new GraphObjectCondition(this, this.internal, condition);
		g.setCornerRadius(0); 
		this.editPane.getChildren().add(g);
		
		// Update / Display
		updateRouteElement(condition, g);
		nodes.add(g);
		return g;
	}

	private void processConditionNode_temp(DServiceDefinition source, DConditionElement condition) {
		if ( source == null ) { // USER CLICKED NEW
			condition.setSize(ServiceChainHelper.getDefaultServiceChainElementWidth(), ServiceChainHelper.getDefaultServiceChainElementWidth());
			condition.setPosition(ServiceChainHelper.getDefaultServiceChainElementX(), ServiceChainHelper.getDefaultServiceChainElementY());
			condition.setColor(ServiceChainHelper.getDefaultConditionColor());
			condition.setName("Condition");
		} else { // Create one based off service def
			condition.setCondition(source.getCondition());
			
			// HACKY. Must be RESET upon saving...
			DRouteElementI parentElement = getRouteElement(source.getSourceId());
			source.setSourceId(condition.getDestinationId());
			if ( parentElement != null ) {
				// Mark that condition comes FROM parent
				condition.setSourceId(parentElement.getDestinationId());
				
				// Force source element to come from condition
				source.setSourceId(condition.getDestinationId());
				/*for (GraphObject node : nodes) {
					DRouteElementI element = node.getRouteElement();
					if ( element instanceof DRouteElement && StringUtils.equalsIgnoreCase(element.getSourceId(), parentElement.getDestinationId())) {
						((DRouteElement)element).setSourceId(condition.getDestinationId());
					}
				}*/
			}
			
			// Setup meta
			if ( source.getConditionMeta() != null ) {
				source.getConditionMeta().setLinkedToId(source.getDestinationId());
				condition.setConditionMeta(source.getConditionMeta());
				condition.setPosition(source.getConditionMeta().getX(), source.getConditionMeta().getY());
				condition.setSize(source.getConditionMeta().getWidth(), source.getConditionMeta().getHeight());
				condition.setColor(source.getConditionMeta().getColor());
				condition.setName(source.getConditionMeta().getName());
			} else {
				condition.setSize(ServiceChainHelper.getDefaultServiceChainElementWidth(), ServiceChainHelper.getDefaultServiceChainElementWidth());
				condition.setPosition(ServiceChainHelper.getDefaultServiceChainElementX(), ServiceChainHelper.getDefaultServiceChainElementY());
				condition.setColor(ServiceChainHelper.getDefaultConditionColor());
				condition.setName("Condition");
			}
		}
	}

	private GraphObject newRouteElementNode(DServiceChain parent, DRouteElementI routeElement) {
		GraphObject g = null;
		if ( routeElement instanceof DConditionElement ) {
			g = new GraphObjectCondition(this, parent, (DConditionElement) routeElement);
		}else if ( routeElement instanceof DServiceDefinition ) {
			g = new GraphObject(this, parent, routeElement);
		}else if (routeElement instanceof DServiceChain) {
			g = new GraphObject(this, parent, routeElement);
			g.setCornerAsPercent();
		} else {
			//
		}
		g.setCornerRadius(3);
		
		final GraphObject graphObject = g;

		updateRouteElement(routeElement, graphObject);
		this.editPane.getChildren().add(graphObject);

		routeElement.getOnChangedEvent().connect((args) -> {
			updateRouteElement(routeElement, graphObject);
			if ( routeElement != this.internal && !ignoreChangeEvents ) {
				this.internal.getOnChangedEvent().fire(routeElement, args);
			}
		});

		nodes.add(graphObject);
		return graphObject;
	}

	private void updateRouteElement(DRouteElementI routeElement, GraphObject g) {
		g.setFill(ColorHelper.fromHexString(routeElement.getColor()));
		g.setName(routeElement.getName());
		g.setPrefSize(routeElement.getWidth(), routeElement.getHeight());
		g.setTranslateX(routeElement.getX());
		g.setTranslateY(routeElement.getY());

		if (routeElement instanceof DServiceChain) {
			g.setName(((DServiceChain) routeElement).getHandlerId());
		}
	}

	private double round(double x) {
		return Math.floor(x / 20d) * 20d;
	}
	
	/**
	 * Returns the cloned service chain derived from the original service chain. This is the "working copy"
	 */
	public DServiceChain getServiceChain() {
		return internal;
	}

	public void setSelectedNode(GraphObject newNode) {
		GraphObject oldNode = selectedNode;
		selectedNode = newNode;

		if (oldNode != null) {
			oldNode.setStyle("-fx-border-style: none;");
		}

		if (newNode != null) {
			newNode.setStyle("-fx-border-color: orange; -fx-border-width: 3; -fx-border-style: segments(10, 10) line-cap square;");
		}
	}
	
	public List<GraphObject> getGraphObjectsUnmodifyable() {
		return Arrays.asList(nodes.toArray(new GraphObject[nodes.size()]));
	}
	
	public List<DRouteElementI> getGraphObjectRoutesUnmodifyable() {
		List<DRouteElementI> routes = new ArrayList<DRouteElementI>();
		for (GraphObject g : nodes) {
			if ( g.getRouteElement() != null )
				routes.add(g.getRouteElement());
		}
		
		return Arrays.asList(routes.toArray(new DRouteElementI[routes.size()]));
	}

	public GraphObject getGraphObjectFromRoute(DRouteElementI routeElement) {
		if ( routeElement == null )
			return null;
		
		for (Node node : this.editPane.getChildrenUnmodifiable()) {
			if (node instanceof GraphObject) {
				if (((GraphObject) node).getRouteElement().equals(routeElement)) {
					return (GraphObject) node;
				}
			}
		}
		
		return null;
	}
}
