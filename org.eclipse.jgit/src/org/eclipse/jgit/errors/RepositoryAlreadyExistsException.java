/********************************************************************************
 * Copyright (c) 2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

package org.eclipse.jgit.errors;

import java.io.IOException;

/**
 * Attempt to add a repository that already exists on one of the nodes.
 */
public class RepositoryAlreadyExistsException extends IOException {
        private static final long serialVersionUID = 1L;

        /**
         * Constructs an RepositoryAlreadyExistsException with the specified detail
         * message.
         *
         * @param s
         *            message
         */
        public RepositoryAlreadyExistsException(final String s) {
          super(s);
        }
}
