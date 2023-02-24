/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.test.jaxr.scout.publish.infomodel;

import org.jboss.test.jaxr.scout.JaxrBaseTestCase;

import javax.xml.registry.BusinessLifeCycleManager;
import javax.xml.registry.BusinessQueryManager;
import javax.xml.registry.Connection;
import javax.xml.registry.JAXRException;
import javax.xml.registry.LifeCycleManager;
import javax.xml.registry.RegistryService;
import javax.xml.registry.infomodel.Association;
import javax.xml.registry.infomodel.Concept;
import javax.xml.registry.infomodel.Key;
import javax.xml.registry.infomodel.Organization;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 * Tests confirming Association
 *
 * @author <mailto:Anil.Saldhana@jboss.org>Anil Saldhana
 * @since Mar 9, 2005
 */
public class JaxrConfimAssociationTestCase extends JaxrBaseTestCase
{
    /**
     * Testcase that tests the association between two organizations
     *
     * @throws JAXRException
     */
    public void testConfirmAssociation() throws JAXRException
    {
        String orgTarget = "Target Organization";
        String orgSource = "Source Organization";
        String type = "Implements";
        Key savekey = null;

        Collection associationKeys = null;
        Collection sourceKeys = null;
        Collection targetKeys = null;
        String targetId = null;
        String sourceId = null;
        BusinessQueryManager bqm2 = null;
        BusinessLifeCycleManager blm2 = null;


        try
        {
            login();
            getJAXREssentials();

            // second user.
            Connection con2 = loginSecondUser();
            RegistryService rs2 = con2.getRegistryService();
            blm2 = rs2.getBusinessLifeCycleManager();
            bqm2 = rs2.getBusinessQueryManager();

            Organization target = blm2.createOrganization(blm.createInternationalString(orgTarget));
            Organization source = blm.createOrganization(blm.createInternationalString(orgSource));

            Collection orgs = new ArrayList();
            orgs.add(source);
            br = blm.saveOrganizations(orgs);
            if (br.getExceptions() != null)
            {
                fail(" Source:Save Orgs failed");
            }

            sourceKeys = br.getCollection();
            Iterator iter = sourceKeys.iterator();
            while (iter.hasNext())
            {
                savekey = (Key) iter.next();
            }
            sourceId = savekey.getId();

            String objectType = LifeCycleManager.ORGANIZATION;
            Organization pubSource = (Organization) bqm.getRegistryObject(sourceId, objectType);
            assertNotNull("Source retrieved: ", pubSource.getName().getValue());

            orgs.clear();
            orgs.add(target);
            br = blm2.saveOrganizations(orgs);
            if (br.getExceptions() != null)
            {
                fail("Target:Save Orgs failed");
            }
            targetKeys = br.getCollection();
            iter = targetKeys.iterator();
            while (iter.hasNext())
            {
                savekey = (Key) iter.next();
            }
            targetId = savekey.getId();

            Organization pubTarget = (Organization) bqm2.getRegistryObject(targetId, objectType);
            assertNotNull("Target: ", pubTarget.getName().getValue());

            Concept associationType = getAssociationConcept(type);
            if (associationType == null)
                fail(" getAssociationConcept returned null");

            Association a = blm.createAssociation(pubTarget, associationType);
            a.setSourceObject(pubSource);

            blm2.confirmAssociation(a);

            Collection associations = new ArrayList();
            associations.add(a);
            br = blm2.saveAssociations(associations, false);
            if (br.getExceptions() != null)
            {
                fail(" Save Association did not complete due to errors");
            }

            associationKeys = br.getCollection();
            iter = associationKeys.iterator();

            Collection associationTypes = new ArrayList();
            associationTypes.add(associationType);
            //confirmedByCaller = false, confirmedByOtherParty = true.
            br = bqm.findCallerAssociations(null,
                    new Boolean(false),
                    new Boolean(true), associationTypes);
            if (br.getExceptions() != null)
            {
                fail(" Find Caller Association failed");
            }
            associations = br.getCollection();
            if (associations.size() == 0)
            {
                fail(" Retrieving Associations failed");
            }
            iter = associations.iterator();
            while (iter.hasNext())
            {
                a = (Association) iter.next();
            }

            assertNotNull("Association type:", a.getAssociationType().getValue());
            if (a.isConfirmed())
            {
                fail("FAIL: isConfirmed returned true  ");
            }
            if (a.isConfirmedBySourceOwner())
            {
                fail("FAIL: isConfirmedBySourceOwner returned true  ");
            }

            blm.confirmAssociation(a);
            br = blm.saveAssociations(associations, false);
            if (br.getExceptions() != null)
            {
                fail("Error:  saveAssociations failed  ");
            }


            br = bqm.findCallerAssociations(null, new Boolean(true), new Boolean(true), associationTypes);

            if (br.getExceptions() != null)
            {
                fail("Error:  findCallerAssociations failed  ");
            }

            associations = br.getCollection();
            iter = associations.iterator();
            while (iter.hasNext())
            {
                a = (Association) iter.next();
            }

            if (!(a.isConfirmed()))
            {
                fail("FAIL: isConfirmed incorrectly returned false ");
            }

            if (!(a.isConfirmedBySourceOwner()))
            {
                fail("FAIL: isConfirmedBySourceOwner incorrectly returned false ");
            }
        } catch (Exception e)
        {
            e.printStackTrace();
            fail("Caught unexpected exception: " + e.getMessage());
        } finally
        {
            // Clean up
            try
            {
                blm2.deleteOrganizations(targetKeys);
                blm.deleteOrganizations(sourceKeys);
            } catch (JAXRException je)
            {
                fail("Error: not able to delete registry objects");
            }
        }
    }

}
