package com.sizov.vitaly.photogallery;

import java.util.List;

// Data model

public class Flickr {

    public Photos photos;

    public static class Photos {

        public List<Photo> photo;
        private int page;

        public static class Photo {
            public String id;
            public String title;
            public String url_s;
        }
    }
}
