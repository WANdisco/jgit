/*
 * Copyright (C) 2016, David Pursehouse <david.pursehouse@gmail.com> and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */
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
package org.eclipse.jgit.lfs.errors;

import java.text.MessageFormat;

import org.eclipse.jgit.lfs.internal.LfsText;

/**
 * Thrown when the repository does not exist for the user.
 *
 * @since 4.5
 */
public class LfsRepositoryNotFound extends LfsException {

    private static final long serialVersionUID = 1L;

    /**
     * Exception defining when an LFS Repository cannot be found.
     *
     * @param name the repository name.
     *
     */
    public LfsRepositoryNotFound(String name) {
        super(MessageFormat.format(LfsText.get().repositoryNotFound, name));
    }

    /**
     * Exception defining when an LFS Repository cannot be found.
     * @param e Further Exception details
     */
    public LfsRepositoryNotFound(Exception e)
    {
        // assuming someone has raised a general exception and wishes to convey it as
        // a repo not found just copy across the message they generated.
        super(e.getMessage());
    }
}
