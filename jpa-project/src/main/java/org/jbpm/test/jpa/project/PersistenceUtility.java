package org.jbpm.test.jpa.project;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

public class PersistenceUtility {

	private static EntityManagerFactory emf;
	
	public static EntityManagerFactory getEmf(ClassLoader cl) {
		if (emf != null) {
			return emf;
		}
		// not yet created, attempt to bootstrap it
		ClassLoader tccl = Thread.currentThread().getContextClassLoader();
		
		try {
			// override tccl so persistence unit can be found from within kjar
			Thread.currentThread().setContextClassLoader(cl);
			
			emf = Persistence.createEntityManagerFactory("org.jbpm.custom");
			
			return emf;
		} finally {
			Thread.currentThread().setContextClassLoader(tccl);
		}
	}
}
