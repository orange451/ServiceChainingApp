package dev.anarchy.ui.control;

import dev.anarchy.common.DConditionElement;
import dev.anarchy.common.DServiceChain;
import dev.anarchy.ui.control.servicechain.ConditionEditor;
import dev.anarchy.ui.control.servicechain.ServiceChainEditor;
import dev.anarchy.ui.util.IconHelper;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;

public class GraphObjectCondition extends GraphObject {
	public GraphObjectCondition(ServiceChainEditor editor, DServiceChain serviceChain, DConditionElement routeElement) {
		super(editor, serviceChain, routeElement);
	}

	@Override
	public DConditionElement getRouteElement() {
		return (DConditionElement) super.getRouteElement();
	}
	
	@Override
	protected void onDelete() {
		this.getEditor().removeNode(this);
	}
	
	@Override
	protected void updateContext(ContextMenu context) {		
		MenuItem option = new MenuItem("Configure", IconHelper.GEAR.create());
		option.setOnAction((event) -> {
			new ConditionEditor(this.getRouteElement()).show();
		});
		context.getItems().add(option);
		
		super.updateContext(context);
	}
}
