/*
 * eXtended Objects - Neo4j - Gremlin Query Support
 *
 * Copyright (C) 2014 SMB GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.smbtec.xo.neo4j.query.gremlin;

import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.hamcrest.collection.IsIterableContainingInAnyOrder;
import org.hamcrest.collection.IsIterableWithSize;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import com.buschmais.xo.api.Example;
import com.buschmais.xo.api.Query.Result;
import com.buschmais.xo.api.XOException;
import com.buschmais.xo.api.XOManager;
import com.buschmais.xo.api.bootstrap.XOUnit;
import com.smbtec.xo.neo4j.query.gremlin.composite.Person;
import com.smbtec.xo.tinkerpop.blueprints.api.TinkerPopDatastoreSession;
import com.smbtec.xo.tinkerpop.blueprints.api.annotation.Gremlin;
import com.smbtec.xo.tinkerpop.blueprints.test.AbstractTinkerPopXOManagerTest;
import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.KeyIndexableGraph;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@RunWith(Parameterized.class)
public class GremlinQueryTest extends AbstractTinkerPopXOManagerTest {

    private Person john;
    private Person mary;

    public GremlinQueryTest(XOUnit xoUnit) {
        super(xoUnit);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> getXOUnits() throws URISyntaxException {
        return xoUnits(Person.class);
    }

    @Before
    public void populate() {
        XOManager xoManager = getXoManager();
        xoManager.currentTransaction().begin();
        Graph graph = xoManager.getDatastoreSession(TinkerPopDatastoreSession.class).getGraph();
        ((KeyIndexableGraph) graph).createKeyIndex("firstname", com.tinkerpop.blueprints.Vertex.class);
        ((KeyIndexableGraph) graph).createKeyIndex("lastname", com.tinkerpop.blueprints.Vertex.class);
        john = xoManager.create(new Example<Person>() {

            @Override
            public void prepare(Person example) {
                example.setFirstname("John");
                example.setLastname("Doe");
                example.setAge(25);
            }

        }, Person.class);
        mary = xoManager.create(new Example<Person>() {

            @Override
            public void prepare(Person example) {
                example.setFirstname("Mary");
                example.setLastname("Doe");
                example.setAge(20);
            }

        }, Person.class);
        john.getFriends().add(mary);
        xoManager.currentTransaction().commit();
    }

    @Test
    public void selectVertex() {
        getXoManager().currentTransaction().begin();
        Person person = getXoManager().createQuery("g.v(0)", Person.class).using(Gremlin.class).execute()
                .getSingleResult();
        assertThat(person, equalTo(john));
        getXoManager().currentTransaction().commit();
    }

    @Test
    public void selectVertices() {
        getXoManager().currentTransaction().begin();
        Result<Person> result = getXoManager().createQuery("g.V[0..10]", Person.class).using(Gremlin.class).execute();
        assertThat(result, IsIterableContainingInAnyOrder.<Person> containsInAnyOrder(john, mary));
        getXoManager().currentTransaction().commit();
    }

    @Test
    public void selectAttributeOfVertex() {
        getXoManager().currentTransaction().begin();
        String firstName = getXoManager().createQuery("g.v(0).firstname", String.class).using(Gremlin.class).execute()
                .getSingleResult();
        assertThat(firstName, equalTo("John"));
        firstName = getXoManager().createQuery("g.v(0).outE('friends').inV().firstname", String.class).execute()
                .getSingleResult();
        assertThat(firstName, equalTo("Mary"));
        getXoManager().currentTransaction().commit();
    }

    @Test
    public void selectAttributeOfVertices() {
        getXoManager().currentTransaction().begin();
        Result<String> result = getXoManager().createQuery("g.V[0..10].firstname", String.class).using(Gremlin.class)
                .execute();
        assertThat(result, IsIterableContainingInAnyOrder.<String> containsInAnyOrder("John", "Mary"));
        getXoManager().currentTransaction().commit();
    }

    @Test
    public void selectVertexByAttribute() {
        getXoManager().currentTransaction().begin();
        Person person = getXoManager().createQuery("g.V('firstname','John')", Person.class).using(Gremlin.class)
                .execute().getSingleResult();
        assertThat(person, equalTo(john));
        getXoManager().currentTransaction().commit();
    }

    @Test
    public void countVertices() {
        XOManager xoManager = getXoManager();
        xoManager.currentTransaction().begin();
        Long count = xoManager.createQuery("g.V('firstname','John').count()", Long.class).using(Gremlin.class)
                .execute().getSingleResult();
        assertThat(count, equalTo(1L));
        xoManager.currentTransaction().commit();
    }

    @Test
    public void countEdges() {
        XOManager xoManager = getXoManager();
        xoManager.currentTransaction().begin();
        Long count = xoManager.createQuery("g.v(0).outE().count()", Long.class).using(Gremlin.class).execute()
                .getSingleResult();
        assertThat(count, equalTo(1L));
        xoManager.currentTransaction().commit();
    }

    @Test(expected = XOException.class)
    public void inOutEdges() {
        getXoManager().currentTransaction().begin();
        // this query must fail, since we do not have a typed relationship
        getXoManager().createQuery("g.v(0).outE('friends')").using(Gremlin.class).execute().getSingleResult();
        getXoManager().currentTransaction().commit();
    }

    @Test
    public void ages() {
        getXoManager().currentTransaction().begin();
        Result<Long> result = getXoManager().createQuery("g.V('lastname','Doe').outE('friends').inV().age", Long.class)
                .using(Gremlin.class).execute();
        assertThat(result, IsIterableWithSize.<Long> iterableWithSize(1));
        getXoManager().currentTransaction().commit();
    }

    @Test
    public void parameterizedQuery() {
        XOManager xoManager = getXoManager();
        xoManager.currentTransaction().begin();
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("me", 0);
        String result = xoManager.createQuery("g.v(me).firstname", String.class).withParameters(parameters)
                .using(Gremlin.class).execute().getSingleResult();
        assertThat(result, equalTo("John"));
        result = xoManager.createQuery("g.V.filter(){it.firstname==name}.lastname", String.class)
                .withParameter("name", "John").using(Gremlin.class).execute().getSingleResult();
        assertThat(result, equalTo("Doe"));
        result = xoManager.createQuery("g.V('firstname',name).lastname", String.class).withParameter("name", "John")
                .using(Gremlin.class).execute().getSingleResult();
        assertThat(result, equalTo("Doe"));
        xoManager.currentTransaction().commit();
    }

}
