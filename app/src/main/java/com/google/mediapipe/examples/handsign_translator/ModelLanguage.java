package com.google.mediapipe.examples.handsign_translator;

public class ModelLanguage {

    String langCode;
    String langTitle;

    public String getLangCode() {
        return langCode;
    }

    public void setLangCode(String langCode) {
        this.langCode = langCode;
    }

    public String getLangTitle() {
        return langTitle;
    }

    public void setLangTitle(String langTitle) {
        this.langTitle = langTitle;
    }

    public ModelLanguage(String langCode, String langTitle) {
        this.langCode = langCode;
        this.langTitle = langTitle;
    }


}
