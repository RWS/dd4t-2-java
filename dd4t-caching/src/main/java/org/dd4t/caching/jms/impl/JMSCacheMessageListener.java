/*
 * Copyright (c) 2015 SDL, Radagio & R. Oudshoorn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dd4t.caching.jms.impl;

import com.tridion.cache.CacheEvent;
import jakarta.annotation.Resource;
import org.dd4t.caching.CacheInvalidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.MessageListener;
import jakarta.jms.ObjectMessage;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

/**
 * @author Mihai Cadariu
 */
public class JMSCacheMessageListener implements MessageListener {

    private static final Logger LOG = LoggerFactory.getLogger(JMSCacheMessageListener.class);
    @Resource
    protected CacheInvalidator cacheInvalidator;
    @Resource
    private JMSCacheMonitor monitor;

    private boolean tridionIsNamespaceAware;

    public JMSCacheMessageListener() {
        tridionIsNamespaceAware = checkNamespaceAware();
    }

    protected boolean checkNamespaceAware() {
        try {
            Class.forName("com.tridion.util.NamespacePrefixWrapper", false, this.getClass().getClassLoader());
            LOG.info("This tridion version is namespace aware (Tridion version is 8.5+)");
            return true;
        } catch (ClassNotFoundException e) {
            LOG.info("This tridion version is not namespace aware (Tridion version is < 8.5)");
        }
        return false;
    }

    public void setMonitor(JMSCacheMonitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public void onMessage(Message message) {
        CacheEvent event = getCacheEvent(message);
        if (event != null) {
            switch (getCacheEventType(event)) {
                case CacheEvent.INVALIDATE:
                    LOG.debug("Invalidate " + event);
                    Serializable key = event.getKey();
                    cacheInvalidator.invalidate(stripNamespaceIfRequired(key.toString()));
                    monitor.setMQServerStatusUp();
                    break;

                case CacheEvent.FLUSH:
                    LOG.debug("Flush " + event);
                    monitor.setMQServerStatusUp();
                    break;
                default:
                    break;
            }
        }
    }

    protected String stripNamespaceIfRequired(String key) {
        if (tridionIsNamespaceAware && isNotEmpty(key) && key.indexOf(":") > 0) {
            return key.substring(key.indexOf(":") + 1);
        }
        return key;
    }

    private CacheEvent getCacheEvent(Message message) {
        CacheEvent event = null;

        try {
            if (message instanceof ObjectMessage) {
                ObjectMessage objectMessage = (ObjectMessage) message;
                Serializable serializable = objectMessage.getObject();
                if (serializable instanceof CacheEvent) {
                    event = (CacheEvent) serializable;
                } else {
                    LOG.error("JMS message is not a com.tridion.cache.CacheEvent");
                }
            } else {
                LOG.error("Unknown message type received: " + message.getClass().getName());
            }
        } catch (JMSException jmse) {
            LOG.error("Cannot read JMS message", jmse);
        }

        return event;
    }

    /**
     * in Tridion 9 the CacheEvent changed a method name.
     *
     * @param event the CacheEvent
     * @return CacheEvent if found.
     */
    private static int getCacheEventType(CacheEvent event) {

        Method getEventTypeMethod = null;

        try {
            getEventTypeMethod = event.getClass().getMethod("getEventType");
        } catch (NoSuchMethodException e) {
            LOG.trace("We're in 11.5");
        }

        if (getEventTypeMethod == null) {
            try {
                getEventTypeMethod = event.getClass().getMethod("getType");
            } catch (NoSuchMethodException e) {
                LOG.trace("We're in 6.x, 7.1, 8.X");
            }
        }

        if (getEventTypeMethod != null) {
            try {
                return (int) getEventTypeMethod.invoke(event);
            } catch (IllegalAccessException | InvocationTargetException e) {
                LOG.error("Could not process CacheEvent", e);
            }
        }
        LOG.error("Method not found on CacheEvent.");
        return -1;
    }

    /**
     * Set the cache agent.
     */
    public void setCacheInvalidator(CacheInvalidator cacheAgent) {
        cacheInvalidator = cacheAgent;
    }
}
