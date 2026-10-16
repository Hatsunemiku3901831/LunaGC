package emu.grasscutter.game.player;

import dev.morphia.annotations.Entity;

import emu.grasscutter.net.proto.MusicGameBookSettingsOuterClass.MusicGameBookSettings;

/** The seven native preference values; local calibration and future fields are not stored here. */
@Entity
public final class MusicGameBookSettingsData {
    private int nhieopaeflf;
    private int dbginecaknf;
    private int ofkmliacihc;
    private boolean fpcnmidoplo;
    private boolean dkokbjojgap;
    private boolean jpmnhohbkca;
    private boolean jdpojffpmhn;

    public MusicGameBookSettingsData() {}

    public MusicGameBookSettingsData(MusicGameBookSettings values) {
        nhieopaeflf = values.getNHIEOPAEFLF();
        dbginecaknf = values.getDBGINECAKNF();
        ofkmliacihc = values.getOFKMLIACIHC();
        fpcnmidoplo = values.getFPCNMIDOPLO();
        dkokbjojgap = values.getDKOKBJOJGAP();
        jpmnhohbkca = values.getJPMNHOHBKCA();
        jdpojffpmhn = values.getJDPOJFFPMHN();
    }

    public MusicGameBookSettings toProto() {
        return MusicGameBookSettings.newBuilder()
                .setNHIEOPAEFLF(nhieopaeflf)
                .setDBGINECAKNF(dbginecaknf)
                .setOFKMLIACIHC(ofkmliacihc)
                .setFPCNMIDOPLO(fpcnmidoplo)
                .setDKOKBJOJGAP(dkokbjojgap)
                .setJPMNHOHBKCA(jpmnhohbkca)
                .setJDPOJFFPMHN(jdpojffpmhn)
                .build();
    }
}
