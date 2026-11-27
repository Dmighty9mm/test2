package net.plebmc.client.apollo.proto;

/**
 * A malformed protobuf or JSON payload. Always caught by the Apollo
 * router: a bad message is logged and dropped, never fatal.
 */
public class ProtoException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public ProtoException(String message) {
                super(message);
        }
}
