package org.opendaylight.usc.util;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.List;

import org.opendaylight.usc.manager.UscConfigurationServiceImpl;
import org.opendaylight.usc.manager.UscManagerService;
import org.opendaylight.usc.manager.UscMonitorService;
import org.opendaylight.usc.manager.UscSecureServiceImpl;
import org.opendaylight.usc.manager.api.UscConfigurationService;
import org.opendaylight.usc.manager.api.UscSecureService;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UscServiceUtils {
    private static final Logger LOG = LoggerFactory
            .getLogger(UscServiceUtils.class);

    /**
     * Register a Service in the OSGi service registry
     *
     * @param clazz
     *            The target class
     * @param instance
     *            of the object exporting the service
     * @param properties
     *            The properties to be attached to the service registration
     * @return true if registration happened, false otherwise
     */
    @SuppressWarnings("unchecked")
    public static <S, B> boolean registerService(B bundle, Class<S> clazz,
            S service, Dictionary<String, Object> properties) {
        try {
            BundleContext bCtx = FrameworkUtil.getBundle(bundle.getClass())
                    .getBundleContext();
            if (bCtx == null) {
                LOG.error("Could not retrieve the BundleContext");
                return false;
            }

            ServiceRegistration<S> registration = (ServiceRegistration<S>) bCtx
                    .registerService(clazz.getName(), service, properties);
            if (registration == null) {
                LOG.error("Failed to register {} for instance {}", clazz,
                        service);
                return false;
            }
            return true;
        } catch (Exception e) {
            LOG.error("Exception {} while registering the service {}",
                    e.getMessage(), service.toString());
        }
        return false;
    }

    /**
     * Retrieve only first one Instance of a Service
     *
     * @param clazz
     *            The target class
     * @param bundle
     *            The caller
     * @return the service list matching the request
     */
    // public static <S,B> S getService(Class<S> clazz, B bundle){
    // BundleContext bundleContext =
    // FrameworkUtil.getBundle(bundle.getClass()).getBundleContext();
    // ServiceReference<S> serviceReference =
    // bundleContext.getServiceReference(clazz);
    // return bundleContext.getService(serviceReference);
    // }
    /**
     * Retrieve only first one Instance of a Service
     *
     * @param clazz
     *            The target class
     * @return the service list matching the request
     */
    @SuppressWarnings("unchecked")
    public static <S, B> S getService(Class<S> clazz) {
        Bundle uscBundle = FrameworkUtil.getBundle(UscManagerService
                .getInstance().getClass());
        if (uscBundle != null) {
            BundleContext bundleContext = uscBundle.getBundleContext();
            ServiceReference<S> serviceReference = bundleContext
                    .getServiceReference(clazz);
            if (serviceReference == null) {
                LOG.error("Failed to get the service reference for class:"
                        + clazz.getName());
                return null;
            }
            return bundleContext.getService(serviceReference);
        } else {
            LOG.warn("UscManagerService is not exist as a bundle!");
            if (clazz.equals(UscConfigurationService.class)) {
                return (S) UscConfigurationServiceImpl.getInstance();
            }
            if (clazz.equals(UscSecureService.class)) {
                return (S) UscSecureServiceImpl.getInstance();
            }
            if (clazz.equals(UscMonitorService.class)) {
                return (S) UscMonitorService.getInstance();
            }
            return null;
        }
    }

    /**
     * Retrieve all the Instances of a Service, optionally filtered via
     * serviceFilter if non-null else all the results are returned if null
     *
     * @param clazz
     *            The target class
     * @param bundle
     *            The caller
     * @param serviceFilter
     *            LDAP filter to be applied in the search
     * @return the service list matching the request
     */
    public static <S, B> List<S> getServices(Class<S> clazz, B bundle,
            String serviceFilter) {
        ArrayList<S> retList = new ArrayList<S>();
        try {
            BundleContext bCtx = FrameworkUtil.getBundle(bundle.getClass())
                    .getBundleContext();

            @SuppressWarnings("unchecked")
            ServiceReference<S>[] services = (ServiceReference<S>[]) bCtx
                    .getServiceReferences(clazz.getName(), serviceFilter);

            if (services != null) {
                for (int i = 0; i < services.length; i++) {
                    retList.add(bCtx.getService(services[i]));
                }
            }
        } catch (Exception e) {
            LOG.error("Instance reference is NULL");
        }
        return retList;
    }
}
