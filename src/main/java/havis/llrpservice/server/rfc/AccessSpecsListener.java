package havis.llrpservice.server.rfc;

interface AccessSpecsListener {
	/**
	 * An access spec has been removed eg. because the max. operation count has
	 * reached.
	 */
	public void removed(long accessSpecsId);
}
