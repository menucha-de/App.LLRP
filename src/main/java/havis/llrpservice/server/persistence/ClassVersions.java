package havis.llrpservice.server.persistence;

import java.util.HashMap;
import java.util.Map;

import havis.llrpservice.server.service.data.ROAccessReportEntity;
import havis.llrpservice.xml.configuration.LLRPServerConfigurationType;
import havis.llrpservice.xml.configuration.LLRPServerInstanceConfigurationType;

public class ClassVersions {
	private static Map<Class<?>, String> versions = new HashMap<>();

	static {
		versions.put(LLRPServerConfigurationType.class, "1.0");
		versions.put(LLRPServerInstanceConfigurationType.class, "1.0");
		versions.put(ROAccessReportEntity.class, "1.0");
	}

	public static String get(Class<?> clazz) {
		return versions.get(clazz);
	}
}
