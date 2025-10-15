/*
 * Copyright (C) 2010, Google Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package org.eclipse.jgit.http.test;

import static org.eclipse.jgit.util.HttpSupport.HDR_ACCEPT;
import static org.eclipse.jgit.util.HttpSupport.HDR_PRAGMA;
import static org.eclipse.jgit.util.HttpSupport.HDR_USER_AGENT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.junit.http.AccessEvent;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevBlob;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.FetchConnection;
import org.eclipse.jgit.transport.HttpTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.TransportHttp;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.http.HttpConnectionFactory;
import org.junit.Before;
import org.junit.Test;

public class DumbClientDumbServerTest extends AllFactoriesHttpTestCase {
	private Repository remoteRepository;

	private URIish remoteURI;

	private RevBlob A_txt;

	private RevCommit A, B;

	public DumbClientDumbServerTest(HttpConnectionFactory cf) {
		super(cf);
	}

	@Override
	@Before
	public void setUp() throws Exception {
		super.setUp();

		final TestRepository<Repository> src = createTestRepository();
		final File srcGit = src.getRepository().getDirectory();
		final URI base = srcGit.getParentFile().toURI();

		ServletContextHandler app = server.addContext("/git");
		app.setResourceBase(base.toString());
		ServletHolder holder = app.addServlet(DefaultServlet.class, "/");
		// The tmp directory is symlinked on OS X
		holder.setInitParameter("aliases", "true");
		server.setUp();

		remoteRepository = src.getRepository();
		remoteURI = toURIish(app, srcGit.getName());
		StoredConfig cfg = remoteRepository.getConfig();
		cfg.setInt("protocol", null, "version", 0);
		cfg.save();

		A_txt = src.blob("A");
		A = src.commit().add("A_txt", A_txt).create();
		B = src.commit().parent(A).add("A_txt", "C").add("B", "B").create();
		src.update(master, B);
	}

	@Test
	public void testListRemote() throws IOException {
		Repository dst = createBareRepository();

		assertEquals("http", remoteURI.getScheme());

		Map<String, Ref> map;
		try (Transport t = Transport.open(dst, remoteURI)) {
			// I didn't make up these public interface names, I just
			// approved them for inclusion into the code base. Sorry.
			// --spearce
			//
			assertTrue("isa TransportHttp", t instanceof TransportHttp);
			assertTrue("isa HttpTransport", t instanceof HttpTransport);

			try (FetchConnection c = t.openFetch()) {
				map = c.getRefsMap();
			}
		}

		assertNotNull("have map of refs", map);
		assertEquals(2, map.size());

		assertNotNull("has " + master, map.get(master));
		assertEquals(B, map.get(master).getObjectId());

		assertNotNull("has " + Constants.HEAD, map.get(Constants.HEAD));
		assertEquals(B, map.get(Constants.HEAD).getObjectId());

		List<AccessEvent> requests = getRequests();
		assertEquals(2, requests.size());
		assertEquals(0, getRequests(remoteURI, "git-upload-pack").size());

		// TODO smh: This sorting fixes an intermittent failure were the order of requests is occasionally
		//           reversed. I want to eliminate this from the tests at least to start verifying that the
		//           5.12 merge was successful and move on to gerrit. However, it would be good to revisit this and
		//           make sure it's not a bug that the request order is inconsistent.
		requests.sort((lhs, rhs) -> {
			// Sort the requests by reverse lexical ordering so info/refs will be in [0] and head in [1].
			return String.CASE_INSENSITIVE_ORDER.compare(rhs.getPath(), lhs.getPath());
		});

		AccessEvent info = requests.get(0);
		assertEquals("GET", info.getMethod());
		assertEquals(join(remoteURI, "info/refs"), info.getPath());
		assertEquals(1, info.getParameters().size());
		assertEquals("git-upload-pack", info.getParameter("service"));
		assertEquals("no-cache", info.getRequestHeader(HDR_PRAGMA));
		assertNotNull("has user-agent", info.getRequestHeader(HDR_USER_AGENT));
		assertTrue("is jgit agent", info.getRequestHeader(HDR_USER_AGENT)
				.startsWith("JGit/"));
		assertEquals("application/x-git-upload-pack-advertisement, */*", info
				.getRequestHeader(HDR_ACCEPT));
		assertEquals(200, info.getStatus());

		AccessEvent head = requests.get(1);
		assertEquals("GET", head.getMethod());
		assertEquals(join(remoteURI, "HEAD"), head.getPath());
		assertEquals(0, head.getParameters().size());
		assertEquals("no-cache", head.getRequestHeader(HDR_PRAGMA));
		assertNotNull("has user-agent", head.getRequestHeader(HDR_USER_AGENT));
		assertTrue("is jgit agent", head.getRequestHeader(HDR_USER_AGENT)
				.startsWith("JGit/"));
		assertEquals(200, head.getStatus());
	}

	@Test
	public void testInitialClone_Loose() throws Exception {
		Repository dst = createBareRepository();
		assertFalse(dst.getObjectDatabase().has(A_txt));

		try (Transport t = Transport.open(dst, remoteURI)) {
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
		}

		assertTrue(dst.getObjectDatabase().has(A_txt));
		assertEquals(B, dst.exactRef(master).getObjectId());
		fsck(dst, B);

		List<AccessEvent> loose = getRequests(loose(remoteURI, A_txt));
		assertEquals(1, loose.size());
		assertEquals("GET", loose.get(0).getMethod());
		assertEquals(0, loose.get(0).getParameters().size());
		assertEquals(200, loose.get(0).getStatus());
	}

	@Test
	public void testInitialClone_Packed() throws Exception {
		try (TestRepository<Repository> tr = new TestRepository<>(
				remoteRepository)) {
			tr.packAndPrune();
		}

		Repository dst = createBareRepository();
		assertFalse(dst.getObjectDatabase().has(A_txt));

		try (Transport t = Transport.open(dst, remoteURI)) {
			t.fetch(NullProgressMonitor.INSTANCE, mirror(master));
		}

		assertTrue(dst.getObjectDatabase().has(A_txt));
		assertEquals(B, dst.exactRef(master).getObjectId());
		fsck(dst, B);

		List<AccessEvent> req;
		AccessEvent event;

		req = getRequests(loose(remoteURI, B));
		assertEquals(1, req.size());
		event = req.get(0);
		assertEquals("GET", event.getMethod());
		assertEquals(0, event.getParameters().size());
		assertEquals(404, event.getStatus());

		req = getRequests(join(remoteURI, "objects/info/packs"));
		assertEquals(1, req.size());
		event = req.get(0);
		assertEquals("GET", event.getMethod());
		assertEquals(0, event.getParameters().size());
		assertEquals("no-cache", event.getRequestHeader(HDR_PRAGMA));
		assertNotNull("has user-agent", event.getRequestHeader(HDR_USER_AGENT));
		assertTrue("is jgit agent", event.getRequestHeader(HDR_USER_AGENT)
				.startsWith("JGit/"));
		assertEquals(200, event.getStatus());
	}

	@Test
	public void testPushNotSupported() throws Exception {
		final TestRepository src = createTestRepository();
		final RevCommit Q = src.commit().create();
		final Repository db = src.getRepository();

		try (Transport t = Transport.open(db, remoteURI)) {
			try {
				t.push(NullProgressMonitor.INSTANCE, push(src, Q));
				fail("push incorrectly completed against a dumb server");
			} catch (NotSupportedException nse) {
				String exp = "remote does not support smart HTTP push";
				assertEquals(exp, nse.getMessage());
			}
		}
	}
}
