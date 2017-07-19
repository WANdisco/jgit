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