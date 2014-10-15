package org.foxbpm.model;


public class DataVariable extends BaseElement {

	/**
	 * 数据类型
	 */
	protected String dataType;

	/**
	 * 字段名称
	 */
	protected String fieldName;

	/**
	 * 是否列表，暂时没用
	 */
	protected boolean isList;

	/**
	 * 是否持久化
	 */
	protected boolean isPersistence;

	/**
	 * 表达式
	 */
	protected String expression;

	/**
	 * 中文描述
	 */
	protected String documentation;

	/**
	 * 节点编号
	 */
	protected String nodeId;

	/**
	 * 业务类型
	 */
	protected String bizType;

	/**
	 * 是否公有（暂时没用）
	 */
	protected boolean isPubilc;

	public String getDataType() {
		return dataType;
	}

	public void setDataType(String dataType) {
		this.dataType = dataType;
	}

	public boolean isList() {
		return isList;
	}

	public void setList(boolean isList) {
		this.isList = isList;
	}

	public boolean isPersistence() {
		return isPersistence;
	}

	public void setPersistence(boolean isPersistence) {
		this.isPersistence = isPersistence;
	}
	
	public String getFieldName() {
		return fieldName;
	}

	public void setFieldName(String fieldName) {
		this.fieldName = fieldName;
	}

	public String getExpression() {
		return expression;
	}

	public void setExpression(String expression) {
		this.expression = expression;
	}

	public String getDocumentation() {
		return documentation;
	}

	public void setDocumentation(String documentation) {
		this.documentation = documentation;
	}

	public String getNodeId() {
		return nodeId;
	}

	public void setNodeId(String nodeId) {
		this.nodeId = nodeId;
	}

	public boolean isPubilc() {
		return isPubilc;
	}

	public void setPubilc(boolean isPubilc) {
		this.isPubilc = isPubilc;
	}

	public String getBizType() {
		return bizType;
	}

	public void setBizType(String bizType) {
		this.bizType = bizType;
	}
}
