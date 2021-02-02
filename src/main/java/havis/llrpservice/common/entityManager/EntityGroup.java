package havis.llrpservice.common.entityManager;

import java.util.Date;

public class EntityGroup {
	private String groupId;
	private Date creationDate;

	public String getGroupId() {
		return groupId;
	}

	public Date getCreationDate() {
		return creationDate;
	}

	void setCreationDate(Date creationDate) {
		this.creationDate = creationDate;
	}

	public EntityGroup(String groupId) {
		this.groupId = groupId;
		this.creationDate = null;
	}
};
