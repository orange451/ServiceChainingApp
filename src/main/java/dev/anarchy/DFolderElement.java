package dev.anarchy;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

@JsonTypeInfo(use=Id.DEDUCTION)
@JsonSubTypes({@Type(DFolder.class), @Type(DServiceChain.class)})
public interface DFolderElement {
	public void setParent(DFolder parent);
	public DFolder getParent();
	public String getName();
}
