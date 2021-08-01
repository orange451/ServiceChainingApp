package dev.anarchy.ui.control;

import dev.anarchy.common.DRouteElement;
import dev.anarchy.common.DRouteElementI;
import dev.anarchy.common.DServiceChain;
import dev.anarchy.common.util.RouteHelper;
import dev.anarchy.ui.util.IconHelper;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

public class GraphObject extends StackPane {

	private final Label label;
	
	private Color fill;
	
	private double cornerRadius;
	
	private DRouteElementI routeElement;
	
	private ServiceChainEditor editor;
	
	private LinkNode linkerNode;
	
	public GraphObject(ServiceChainEditor editor, DServiceChain serviceChain, DRouteElementI routeElement) {
		this.setAlignment(Pos.CENTER);
		this.setPrefSize(140, 80);
		
		this.editor = editor;
		this.routeElement = routeElement;

		DropShadow shadow = new DropShadow();
		shadow.setRadius(4.0);
		shadow.setOffsetX(0.0);
		shadow.setOffsetY(2.0);
		shadow.setColor(Color.color(0.2, 0.25, 0.25));
		this.setEffect(shadow);
		
		this.setFill(Color.color(0.196, 0.6274, 0.235));
		this.setCornerRadius(256);

		label = new Label("Node");
		label.setMouseTransparent(true);
		this.getChildren().add(label);

		this.setOnMousePressed((event)->{
			editor.setSelectedNode(this);
			event.consume();
		});
		
		linkerNode = new LinkNode(routeElement, serviceChain, this);
		
		this.setOnMouseDragged(event -> {
			if ( event.getButton() != MouseButton.PRIMARY )
				return;
			
			if ( !event.getTarget().equals(this) )
				return;
			
			double hWid = this.getWidth()/2;
			double hHei = this.getHeight()/2;
			double x = round(event.getX() + this.getTranslateX() - hWid);
			double y = round(event.getY() + this.getTranslateY() - hHei);
			this.setTranslateX(x);
			this.setTranslateY(y);
			routeElement.setPosition(x, y);
			event.consume();
		});
		
		this.setOnDragOver(event -> {
			/* data is dragged over the target */
			if (event.getGestureSource() != this ) {
				event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
			}

			event.consume();
		});
		
		this.setOnDragDropped(event -> {
			if ( event.getGestureSource() instanceof LinkNode ) {
				LinkNode droppedFrom = (LinkNode)event.getGestureSource();
				if ( this.getRouteElement() instanceof DRouteElement ) {
					RouteHelper.linkRoutes(serviceChain.getRoutesUnmodifyable(), droppedFrom.getRouteElement(), (DRouteElement) this.getRouteElement());
					droppedFrom.setLinkTo(this.linkerNode);
				} else {
					System.out.println("Cannot link " + droppedFrom.getRouteElement() + " to " + this.getRouteElement() + ". Element must be a RouteElement");
				}
			}
		});
		
		ContextMenu context = new ContextMenu();
		context.setAutoHide(true);
		context.setHideOnEscape(true);

		// Delete context
		if ( !(routeElement instanceof DServiceChain) ) {
			MenuItem option = new MenuItem("Delete", IconHelper.DELETE.create());
			option.setOnAction((event) -> {
				serviceChain.removeRoute((DRouteElement) routeElement);
			});
			context.getItems().add(option);
		}

		// Show context
		this.setOnMouseClicked((event) -> {
			if (event.getButton() == MouseButton.SECONDARY) {
				if (!context.isShowing()) {
					context.show(this, event.getScreenX(), event.getScreenY());
				}
			}
		});
		
		update();
	}
	
	public void setCornerRadius(double x) {
		this.cornerRadius = x;
		update();
	}

	public void setFill(Color color) {
		this.fill = color;
		update();
	}
	
	private boolean percentCorner = false;
	protected void setCornerAsPercent() {
		percentCorner = true;
		update();
	}

	private void update() {
		this.setBackground(new Background(new BackgroundFill(fill, new CornerRadii(cornerRadius,cornerRadius,cornerRadius,cornerRadius, percentCorner), Insets.EMPTY)));
	}

	private double round(double x) {
		return Math.floor(x / 20d) * 20d;
	}

	public void setName(String string) {
		this.label.setText(string);
	}

	public DRouteElementI getRouteElement() {
		return routeElement;
	}

	public ServiceChainEditor getEditor() {
		return this.editor;
	}

	public LinkNode getLinkNode() {
		return this.linkerNode;
	}
}
