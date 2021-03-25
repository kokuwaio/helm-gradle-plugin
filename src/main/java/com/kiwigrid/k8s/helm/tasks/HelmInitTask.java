package com.kiwigrid.k8s.helm.tasks;

import java.io.File;
import java.util.Arrays;
import java.util.stream.Collectors;

import com.kiwigrid.k8s.helm.HelmPlugin;
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
		onlyIf(task -> {
			String version = getVersion();
			logger.debug("found version : " + version + " of helm");
			boolean is30OrNewer = HelmPlugin.isVersion3OrNewer(version);
			if(is30OrNewer) {
				logger.debug("version : " + version + " is higher than 3.0. This operation is a NO-OP");
			}
			return !is30OrNewer;
		});
	}

	@TaskAction
	public void helmInit() {
		String [] result = HelmPlugin.helmExecSuccess(getProject(), this, "init", "--client-only");
		if(logger.isDebugEnabled()){
			logger.debug("result of 'helm init --client-only' : \n" + String.join("\n", result));
		}
	}

	@OutputDirectory
	public File getTaskOutput() {
		// we use the plugin directory as output because nobody messes with it
		return new File(super.getHelmHomeDirectory(), "plugins");
	}
}
