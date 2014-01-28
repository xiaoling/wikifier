package edu.illinois.cs.cogcomp.wikifier.common;

import edu.illinois.cs.cogcomp.wikifier.models.Mention;


/**
 * Configuration presets to ease the pain of going through parameters
 * 
 * @author cheng88
 * 
 */
public enum ParameterPresets {

    DEFAULT {
        public void set() {

        }
    },

    BASELINE {
        public void set() {
            requireLinkability(true);
            enableLexicalSearch(false);
            optimizeCandidateGeneration(false);
            toggleAllInferece(false);
        }
    },

    LEXICAL_SEARCH {
        public void set() {
            toggleAllInferece(false);
        }
    },

    COREF {
        public void set() {
            GlobalParameters.params.USE_RELATIONAL_INFERENCE = false;
        }
    },

    FULL {
        public void set() {
        }
    },

    COMPARE_OLD_WIKIFIER {
        public void set() {
            GlobalParameters.params.UNINDEXED_AS_NULL = false;
        }
    },

    FULL_UNAMBIGUOUS {
        public void set() {
            GlobalParameters.params.EXCLUDE_DISAMBIGUATION_PAGE = true;
        }
    },

    TAC {
        public void set() {
            GlobalParameters.params.DISABLE_LOC_COREF = true;
            // The old version contains many pages that was not disambiguation page
            // but is now a disambiguation page
//            GlobalParameters.params.EXCLUDE_DISAMBIGUATION_PAGE = true;
            // More relaxed fuzzy matches as spell errors are prevalent
            Mention.fuzzyTokenMatchSim = 0.96;
            GlobalParameters.settings.annotateCOREF = true;
        }
    },

    STAND_ALONE_NO_INFERENCE{
        public void set(){
            STAND_ALONE_GUROBI.set();
            GlobalParameters.params.USE_RELATIONAL_INFERENCE = false;
        }
    },
    
    STAND_ALONE_GUROBI{
        public void set(){
            requireLinkability(true);
            
            GlobalParameters.params.DISABLE_LOC_COREF = true;
            GlobalParameters.params.EXCLUDE_DISAMBIGUATION_PAGE = true;
            // Do not use redirects
            GlobalParameters.paths.compressedRedirects = null;

            GlobalParameters.settings.bypassCurator = true;
            GlobalParameters.settings.curatorURL = null;
            GlobalParameters.settings.curatorPort = null;
            // Do not use curator cache
            GlobalParameters.paths.curatorCache = null;
            GlobalParameters.params.USE_COREF = false;
            GlobalParameters.settings.annotateCOREF = false;
        }
    },
    DEMO{
        public void set() {
            GlobalParameters.params.RESOLVE_NOMINAL_COREF = true;
        }
    };

    protected abstract void set();

    protected static void optimizeCandidateGeneration(boolean flag) {
        GlobalParameters.params.GENERATE_CONTEXTUAL_CANDIDATES = flag;
        GlobalParameters.params.USE_SPELL_CHECK = flag;
        GlobalParameters.params.GENERATE_LONG_ENTITIES = flag;
    }

    protected static void enableLexicalSearch(boolean flag) {
        GlobalParameters.params.USE_LEXICAL_SEARCH_HEURSTICS = flag;
        GlobalParameters.params.USE_FUZZY_SEARCH = flag;
    }

    protected static void requireLinkability(boolean flag) {
        if (flag) {
            GlobalParameters.params.minLinkability = 0.05;
            GlobalParameters.params.minSurfaceLen = 3;
        } else {
            GlobalParameters.params.minLinkability = -1;
            GlobalParameters.params.minSurfaceLen = 0;
        }
    }

    protected static void toggleAllInferece(boolean flag) {
        GlobalParameters.params.USE_COREF = flag;
        GlobalParameters.params.USE_RELATIONAL_INFERENCE = flag;
    }

    public void apply() {
        GlobalParameters.settings = SystemSettings.defaultInstance();
        GlobalParameters.paths = GlobalPaths.defaultInstance();
        GlobalParameters.params = WikifierParameters.defaultInstance();

        // Basics
        requireLinkability(false);

        // Candidate generations
        optimizeCandidateGeneration(true);

        // Lexical matching
        enableLexicalSearch(true);

        // Coref
        GlobalParameters.params.USE_COREF = true;

        // Relational and nominal inference
        GlobalParameters.params.USE_RELATIONAL_INFERENCE = true;

        set();

        GlobalParameters.params.preset = this;
    }

}
