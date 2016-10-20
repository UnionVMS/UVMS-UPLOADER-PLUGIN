/*
Developed by the European Commission - Directorate General for Maritime Affairs and Fisheries @ European Union, 2015-2016.

This file is part of the Integrated Fisheries Data Management (IFDM) Suite. The IFDM Suite is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of
the License, or any later version. The IFDM Suite is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
details. You should have received a copy of the GNU General Public License along with the IFDM Suite. If not, see <http://www.gnu.org/licenses/>.

 */
package eu.europa.ec.fisheries.uvms.plugins.service.bean;

import eu.europa.ec.fisheries.uvms.plugins.constants.UploaderConstants;
import eu.europa.ec.fisheries.uvms.plugins.exception.UploaderConfigurationException;
import eu.europa.ec.fisheries.uvms.plugins.properties.bean.PropertiesLoaderServiceBean;
import eu.europa.ec.fisheries.uvms.plugins.service.ModuleWorkConfiguration;
import org.apache.commons.configuration.CompositeConfiguration;
import org.apache.commons.lang3.StringUtils;

import javax.annotation.PostConstruct;
import javax.ejb.DependsOn;
import javax.ejb.EJB;
import javax.ejb.LocalBean;
import javax.ejb.Singleton;
import java.util.*;

/**
 * Created by kovian on 14/10/2016.
 */
@LocalBean
@Singleton
@DependsOn(value = {"PropertiesLoaderServiceBean"})
public class WorkFlowsLoaderBean {

    private String mainDir;
    private String schedulerConfig;
    private Set<ModuleWorkConfiguration> works;

    @EJB
    PropertiesLoaderServiceBean propsBean;

    @PostConstruct
    public void loadFromProperties() throws UploaderConfigurationException {
        CompositeConfiguration props = propsBean.getConfigProps();
        works           = new HashSet<>();
        schedulerConfig = props.getString(UploaderConstants.JOB_SCHEDULER_CONFIG_KEY);
        mainDir         = props.getString(UploaderConstants.UPLOADER_MAIN_DIR_KEY);
        if (StringUtils.isEmpty(mainDir)) {
            throw new UploaderConfigurationException(UploaderConstants.MAIN_DIR_EXC_MESSAGE);
        }
        List<String> supportedModules = Arrays.asList((props.getString(UploaderConstants.SUPPORTED_MODULES_KEY)).split(UploaderConstants.COMMA));
        for (String moduleName : supportedModules) {
            Map<String, String> directories = extractWorkDirectoriesForModuleName(moduleName, props);
            if (checkAllNeededDirectoriesAreCreated(directories)) {
                works.add(buildWorkConfigurationForModule(moduleName, directories, props));
            } else {
                throw new UploaderConfigurationException(UploaderConstants.NEEDED_MORE_DIRS_EXC + moduleName + UploaderConstants.MORE_CONFIGURATION_IS_NEEDED);
            }
        }
    }

    private boolean checkAllNeededDirectoriesAreCreated(Map<String, String> directories) {
        if (directories.get(UploaderConstants.UPLOAD) == null
                || directories.get(UploaderConstants.PROCESSED) == null
                || directories.get(UploaderConstants.REFUSED) == null) {
            return false;
        }
        return true;
    }

    private ModuleWorkConfiguration buildWorkConfigurationForModule(String moduleName, Map<String, String> directories, CompositeConfiguration props) throws UploaderConfigurationException {
        Set<String> supportedFiles;
        try {
            supportedFiles = new HashSet<>((List<String>)props.getProperty(moduleName+UploaderConstants.DOT_UPLOAD_SUPPORTED_FILES_KEY));
        } catch(ClassCastException | NullPointerException ex){
            throw new UploaderConfigurationException(UploaderConstants.NOT_CONFIGURED_SUPPORTED_FILES_FOR_MODULE
                    + moduleName + UploaderConstants.MORE_CONFIGURATION_IS_NEEDED);
        }
        return new ModuleWorkConfiguration(moduleName,directories, supportedFiles);
    }

    private Map<String, String> extractWorkDirectoriesForModuleName(String moduleName, CompositeConfiguration props) {
        Map<String, String> dirsMap = new HashMap<>();
        dirsMap.put(UploaderConstants.UPLOAD,    props.getString(moduleName + UploaderConstants.UPLOAD_DOT_DIR_KEY));
        dirsMap.put(UploaderConstants.REFUSED,   props.getString(moduleName + UploaderConstants.REFUSED_DOT_DIR_KEY));
        dirsMap.put(UploaderConstants.PROCESSED, props.getString(moduleName + UploaderConstants.PROCESSED_DOT_DIR_KEY));
        dirsMap.put(UploaderConstants.FAILED,    props.getString(moduleName + UploaderConstants.FAILED_DOT_DIR_KEY));
        return dirsMap;
    }

    public Set<ModuleWorkConfiguration> getWorks() {
        return works;
    }

    public String getSchedulerConfig() {
        return schedulerConfig;
    }


}
