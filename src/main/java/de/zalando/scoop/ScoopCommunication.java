package de.zalando.scoop;

import akka.cluster.Cluster;

public final class ScoopCommunication {
    public static final class NewScoopListener{
        private final ScoopListener listener;

        public NewScoopListener(ScoopListener listener) {
            this.listener = listener;
        }

        public ScoopListener getListener() {
            return listener;
        }
    }
}
