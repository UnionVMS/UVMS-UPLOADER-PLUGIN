package eu.europa.ec.fisheries.uvms.plugins.uploader;

import eu.europa.ec.fisheries.schema.exchange.plugin.types.v1.PluginType;
import eu.europa.ec.fisheries.schema.exchange.service.v1.CapabilityListType;
import eu.europa.ec.fisheries.schema.exchange.service.v1.ServiceType;
import eu.europa.ec.fisheries.schema.exchange.service.v1.SettingListType;
import eu.europa.ec.fisheries.uvms.exchange.model.constant.ExchangeModelConstants;
import eu.europa.ec.fisheries.uvms.exchange.model.exception.ExchangeModelMarshallException;
import eu.europa.ec.fisheries.uvms.exchange.model.mapper.ExchangeModuleRequestMapper;
import eu.europa.ec.fisheries.uvms.plugins.uploader.constants.UploaderConstants;
import eu.europa.ec.fisheries.uvms.plugins.uploader.mapper.ServiceMapper;
import eu.europa.ec.fisheries.uvms.plugins.uploader.producer.PluginMessageProducer;
import eu.europa.ec.fisheries.uvms.plugins.uploader.service.UploaderTimerService;
import eu.europa.ec.fisheries.uvms.plugins.uploader.service.bean.FileHandlerBean;
import eu.europa.ec.fisheries.uvms.plugins.uploader.service.bean.WorkFlowsLoaderBean;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.ejb.*;
import javax.jms.JMSException;
import java.util.Map;

@Singleton
@Startup
@ConcurrencyManagement(ConcurrencyManagementType.BEAN)
@DependsOn({"PluginMessageProducer", "FileHandlerBean", "UploaderTimerServiceBean", "WorkFlowsLoaderBean"})
public class StartupBean extends PluginDataHolder {

    private static final Logger LOG = LoggerFactory.getLogger(StartupBean.class);

    private boolean isEnabled                    = false;
    private boolean waitingForResponse           = false;
    private String registeredClassName           = StringUtils.EMPTY;

    private static final String FAILED_TO_GET_SETTING_FOR_KEY = "Failed to getSetting for key: ";
    private static final String FAILED_TO_SEND_UNREGISTRATION_MESSAGE_TO = "Failed to send unregistration message to {}";

    @EJB
    private PluginMessageProducer messageProducer;

    @EJB
    private FileHandlerBean fileHandler;

    @EJB
    private UploaderTimerService timerServBean;

    @EJB
    private WorkFlowsLoaderBean workLoader;

    private CapabilityListType plugInCapabilities;
    private SettingListType settingList;
    private ServiceType serviceType;

    @PostConstruct
    public void startup() {

        //This must be loaded first!!! Not doing that will end in dire problems later on!
        super.setPluginApplicaitonProperties(fileHandler.getPropertiesFromFile(PLUGIN_PROPERTIES_KEY));
        registeredClassName = getPLuginApplicationProperty("application.groupid");

        //These can be loaded in any order
        super.setPluginProperties(fileHandler.getPropertiesFromFile(PROPERTIES_KEY));
        super.setPluginCapabilities(fileHandler.getPropertiesFromFile(CAPABILITIES_PROPS_KEY));

        ServiceMapper.mapToMapFromProperties(super.getSettings(), super.getPluginProperties(), getRegisterClassName());
        ServiceMapper.mapToMapFromProperties(super.getCapabilities(), super.getPluginCapabilities(), null);

        plugInCapabilities = ServiceMapper.getCapabilitiesListTypeFromMap(super.getCapabilities());
        settingList = ServiceMapper.getSettingsListTypeFromMap(super.getSettings());

        serviceType = ServiceMapper.getServiceType(
                getRegisterClassName(),
                getApplicationName(),
                "Plugin for listeing to folder for files containing data that should be sent to the flow manually.",
                PluginType.MANUAL,
                getPluginResponseSubscriptionName());

        register();

        LOG.debug("Settings updated in plugin {}", registeredClassName);
        for (Map.Entry<String, String> entry : super.getSettings().entrySet()) {
            LOG.debug("Setting: KEY: {} , VALUE: {}", entry.getKey(), entry.getValue());
        }

        LOG.info("Setting up scheduler service for Folder listening..");
        timerServBean.setUpScheduler(workLoader.getSchedulerConfig());

        LOG.info("PLUGIN STARTED");
    }

    @PreDestroy
    public void shutdown() {
        unregister();
    }


    private void register() {
        LOG.info("Registering to Exchange Module");
        setWaitingForResponse(true);
        try {
            String registerServiceRequest = ExchangeModuleRequestMapper.createRegisterServiceRequest(serviceType, plugInCapabilities, settingList);
            messageProducer.sendEventBusMessage(registerServiceRequest, ExchangeModelConstants.EXCHANGE_REGISTER_SERVICE);
        } catch (JMSException | ExchangeModelMarshallException e) {
            LOG.error(FAILED_TO_SEND_UNREGISTRATION_MESSAGE_TO, ExchangeModelConstants.EXCHANGE_REGISTER_SERVICE,e);
            setWaitingForResponse(false);
        }

    }

    private void unregister() {
        LOG.info("Unregistering from Exchange Module");
        try {
            String unregisterServiceRequest = ExchangeModuleRequestMapper.createUnregisterServiceRequest(serviceType);
            messageProducer.sendEventBusMessage(unregisterServiceRequest, ExchangeModelConstants.EXCHANGE_REGISTER_SERVICE);
        } catch (JMSException | ExchangeModelMarshallException e) {
            LOG.error(FAILED_TO_SEND_UNREGISTRATION_MESSAGE_TO, ExchangeModelConstants.EXCHANGE_REGISTER_SERVICE,e);
        }
    }

    public String getPLuginApplicationProperty(String key) {
        try {
            return (String) super.getPluginApplicaitonProperties().get(key);
        } catch (Exception e) {
            LOG.error(FAILED_TO_GET_SETTING_FOR_KEY,key, getRegisterClassName(),e);
            return null;
        }
    }

    public String getPluginResponseSubscriptionName() {
        return getRegisterClassName() + getSetting("application.responseTopicName");
    }

    public String getResponseTopicMessageName() {
        return getSetting("application.groupid");
    }

    public String getRegisterClassName() {
        return registeredClassName;
    }

    public String getApplicationName() {
        return getSetting("application.name");
    }

    public String getSetting(String key) {
        try {
            LOG.debug("Trying to get setting {} ", registeredClassName + UploaderConstants.DOT + key);
            return super.getSettings().get(registeredClassName + UploaderConstants.DOT + key);
        } catch (Exception e) {
            LOG.error(FAILED_TO_GET_SETTING_FOR_KEY,key, getRegisterClassName(),e);
            return null;
        }
    }

    public boolean isWaitingForResponse() {
        return waitingForResponse;
    }
    public void setWaitingForResponse(boolean waitingForResponse) {
        this.waitingForResponse = waitingForResponse;
    }
    public boolean isIsEnabled() {
        return isEnabled;
    }
    public void setIsEnabled(boolean isEnabled) {
        this.isEnabled = isEnabled;
    }

}
