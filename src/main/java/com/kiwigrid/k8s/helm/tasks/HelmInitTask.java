package com.kiwigrid.k8s.helm.tasks;

import java.io.File;

import com.kiwigrid.k8s.helm.HelmPlugin;
import org.gradle.api.Task;
import org.gradle.api.logging.Logger;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

/**
 * created on 28.03.18.
 *
 * @author Jörg Eichhorn {@literal <joerg.eichhorn@kiwigrid.com>}
 */
public class HelmInitTask extends AbstractHelmTask {

	private final Logger logger = getLogger();

	public HelmInitTask() {
		onlyIf(this::notNewerThan30);
	}

	@TaskAction
	public void helmInit() {
		HelmPlugin.helmExecSuccess(getProject(), this, "init", "--client-only");
	}

	@OutputDirectory
	public File getTaskOutput() {
		// we use the plugin directory as output because nobody messes with it
		return new File(super.getHelmHomeDirectory(), "plugins");
	}

	private boolean notNewerThan30(Task task) {
		String version = getVersion();
		boolean is30OrNewer = HelmPlugin.isVersion3OrNewer(version);
		if(is30OrNewer) {
			logger.lifecycle("version : " + version + " is higher than 3.0. 'HelmInitTask' is a NO-OP");
		} else {
			logger.lifecycle("will issue : './helm init --client-only' in : " + getHelmHomeDirectory());
		}
		return !is30OrNewer;
	}
}
