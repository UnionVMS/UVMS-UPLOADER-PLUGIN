/*
Developed by the European Commission - Directorate General for Maritime Affairs and Fisheries @ European Union, 2015-2016.

This file is part of the Integrated Fisheries Data Management (IFDM) Suite. The IFDM Suite is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of
the License, or any later version. The IFDM Suite is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
details. You should have received a copy of the GNU General Public License along with the IFDM Suite. If not, see <http://www.gnu.org/licenses/>.

 */
package eu.europa.ec.fisheries.uvms.plugins.uploader.producer;

import eu.europa.ec.fisheries.uvms.exchange.model.constant.ExchangeModelConstants;
import eu.europa.ec.fisheries.uvms.message.JMSUtils;
import eu.europa.ec.fisheries.uvms.plugins.uploader.constants.ModuleQueue;
import javax.annotation.PostConstruct;
import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.naming.InitialContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Stateless
@LocalBean
public class PluginMessageProducer {

    private Queue exchangeQueue;
    private Topic eventBus;
    private ConnectionFactory connectionFactory;
    private Connection connection = null;

    private static final Logger LOG = LoggerFactory.getLogger(PluginMessageProducer.class);


    @PostConstruct
    public void resourceLookup() {
        LOG.debug("Open connection to JMS broker");
        InitialContext ctx;
        try {
            ctx = new InitialContext();
        } catch (Exception e) {
            LOG.error("Failed to get InitialContext",e);
            throw new RuntimeException(e);
        }
        connectionFactory = JMSUtils.lookupConnectionFactory();
        try {
            connection = connectionFactory.createConnection();
            connection.start();
        } catch (JMSException ex) {
            LOG.error("Error when open connection to JMS broker");
        }
        exchangeQueue = JMSUtils.lookupQueue(ctx, ExchangeModelConstants.EXCHANGE_MESSAGE_IN_QUEUE);
        eventBus = JMSUtils.lookupTopic(ctx, ExchangeModelConstants.PLUGIN_EVENTBUS);
    }


    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void sendResponseMessage(String text, TextMessage requestMessage) throws JMSException {
    	Connection connection=null;
    	try {
            connection = connectionFactory.createConnection();
            Session session = JMSUtils.connectToQueue(connection);
            TextMessage message = session.createTextMessage();
            message.setJMSDestination(requestMessage.getJMSReplyTo());
            message.setJMSCorrelationID(requestMessage.getJMSMessageID());
            message.setText(text);
            session.createProducer(requestMessage.getJMSReplyTo()).send(message);
        } catch (JMSException e) {
            LOG.error("[ Error when sending jms message. {}] {}",text, e);
            throw new JMSException(e.getMessage());
        } finally {
        	JMSUtils.disconnectQueue(connection);
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public String sendModuleMessage(String text, ModuleQueue queue) throws JMSException {
    	Connection connection = null;
    	try {
            connection = connectionFactory.createConnection();
            final Session session = JMSUtils.connectToQueue(connection);
            TextMessage message = session.createTextMessage();
            message.setText(text);
            switch (queue) {
                case EXCHANGE:
                    session.createProducer(exchangeQueue).send(message);
                    break;
                default:
                    LOG.error("[ Sending Queue is not implemented ]");
                    break;
            }
            return message.getJMSMessageID();
        } catch (JMSException e) {
            LOG.error("[ Error when sending data source message.{} ] {}",text, e);
            throw new JMSException(e.getMessage());
        } finally {
        	JMSUtils.disconnectQueue(connection);
        }
    }

    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public String sendEventBusMessage(String text, String serviceName) throws JMSException {
    	Connection connection = null;
    	try {
            connection = connectionFactory.createConnection();
            final Session session = JMSUtils.connectToQueue(connection);
            TextMessage message = session.createTextMessage();
            message.setText(text);
            message.setStringProperty(ExchangeModelConstants.SERVICE_NAME, serviceName);
            session.createProducer(eventBus).send(message);
            return message.getJMSMessageID();
        } catch (JMSException e) {
            LOG.error("[ Error when sending message. {}] {}",text, e);
            throw new JMSException(e.getMessage());
        } finally {
        	JMSUtils.disconnectQueue(connection);
        }
    }

}
