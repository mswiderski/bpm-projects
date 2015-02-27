package org.jbpm.test.jpa.project;

import java.io.IOException;
import java.io.ObjectInputStream;

import org.drools.persistence.jpa.marshaller.JPAPlaceholderResolverStrategy;

public class ProjectJPAResolverStrategy extends JPAPlaceholderResolverStrategy {

	private ClassLoader classLoader;
	
	public ProjectJPAResolverStrategy(ClassLoader cl) {
		super(org.jbpm.test.jpa.project.PersistenceUtility.getEmf(cl));
		this.classLoader = cl;
	}

	@Override
	public Object unmarshal(Context context, ObjectInputStream ois, byte[] object, ClassLoader classloader) throws IOException,
			ClassNotFoundException {
		return super.unmarshal(context, ois, object, this.classLoader);
	}

}
