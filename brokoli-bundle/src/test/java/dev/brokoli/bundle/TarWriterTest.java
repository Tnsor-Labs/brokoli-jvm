package dev.brokoli.bundle;

import dev.brokoli.bundle.internal.Digests;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;

class TarWriterTest {

    private static final int BLOCK = 512;
    private static final String L_STROKE = String.valueOf((char) 0x141);

    static Stream<Arguments> pathsWrittenByGo() {
        return Stream.of(
                arguments("a".repeat(100), "5a977cd573d98c6f1401351f45e685e176a4b8fe5f536fc9ab2ba7bdf6a9a53b"),
                arguments("a".repeat(101), "1958839e374b24985827203156247af240b13bf8fdb57719f0b9196d90732fd7"),
                arguments("p".repeat(155) + "/" + "n".repeat(100),
                        "f19cc25fcfa26bafedc20ab768e744f328cf71eee23868ba6a37472423194aec"),
                arguments("p".repeat(156) + "/n.class",
                        "0986490665e47428d65e1d4006d84a1cc6cd00952a0ed221b5de681471b060d6"),
                arguments("p".repeat(154) + "/" + "n".repeat(101),
                        "e3be663ec540040b753bdd4b4d2a6ed678a504ff99640f48fd4aa23378dc37c1"),
                arguments("q".repeat(60) + "/" + "r".repeat(60) + "/" + "s".repeat(60) + "/t.class",
                        "427fe212aba837437347ffadf466210aa4f162853f066d459ac329479cd27814"),
                arguments("d".repeat(86) + "/" + L_STROKE + ".class",
                        "dd13afc97a95e187e969f4e826f3f4993f3a419f1e404445d38f0007352d28d0"),
                arguments("d".repeat(300) + "/x.class",
                        "95d8ea7433070b5d39fd1f9d3ba72c0c5d1f36029f7231e5c72de29219dacca8"),
                arguments("z/" + L_STROKE.repeat(60) + ".class",
                        "b796d98d0c728f5b476157fe548a11997dfd57782a83de1fb5053145d3ebfaeb"),
                arguments("a" + L_STROKE.repeat(45), "b57cd2d8292c4c85776020ad75464cf6468448cc06f710389dd092aeaaf077e8"));
    }

    @ParameterizedTest
    @MethodSource("pathsWrittenByGo")
    void writesEachPathAsGo126ArchiveTarDoes(String path, String goSha256) throws IOException {
        assertThat(Digests.sha256Hex(tar(path))).isEqualTo(goSha256);
    }

    @Test
    void writesAnAsciiPathOfExactlyOneHundredBytesInTheNameField() throws IOException {
        String path = "a/" + "b".repeat(98);

        byte[] header = firstHeader(path);

        assertThat(field(header, 0, 100)).isEqualTo(path);
        assertThat(field(header, 345, 155)).isEmpty();
        assertThat((char) header[156]).isEqualTo('0');
    }

    @Test
    void splitsALongerAsciiPathAtTheLastSlashThatLeavesANameOfAtMostOneHundredBytes() throws IOException {
        String path = "d".repeat(60) + "/" + "e".repeat(60) + "/f.class";

        byte[] header = firstHeader(path);

        assertThat(field(header, 345, 155)).isEqualTo("d".repeat(60) + "/" + "e".repeat(60));
        assertThat(field(header, 0, 100)).isEqualTo("f.class");
    }

    @Test
    void writesAPaxHeaderWhoseRecordLengthCountsItsOwnDigits() throws IOException {
        String path = "a" + "\u0141".repeat(45);

        byte[] tar = tar(path);

        String record = "101 path=" + path + "\n";
        assertThat(record.getBytes(StandardCharsets.UTF_8)).hasSize(101);
        assertThat((char) tar[156]).isEqualTo('x');
        assertThat(field(tar, 0, 100)).isEqualTo("PaxHeaders.0/a");
        assertThat(new String(Arrays.copyOfRange(tar, BLOCK, BLOCK + 101), StandardCharsets.UTF_8)).isEqualTo(record);
        assertThat(field(tar, 2 * BLOCK, 100)).isEqualTo("a");
    }

    @Test
    void writesNumericFieldsAsGoDoes() throws IOException {
        byte[] header = firstHeader("a.class");

        assertThat(Arrays.copyOfRange(header, 100, 108)).isEqualTo("0000600\0".getBytes(StandardCharsets.US_ASCII));
        assertThat(Arrays.copyOfRange(header, 124, 136))
                .isEqualTo("00000000003\0".getBytes(StandardCharsets.US_ASCII));
        assertThat(Arrays.copyOfRange(header, 257, 265)).isEqualTo("ustar\u000000".getBytes(StandardCharsets.US_ASCII));
        assertThat(Arrays.copyOfRange(header, 329, 345))
                .isEqualTo("0000000\u00000000000\0".getBytes(StandardCharsets.US_ASCII));
        assertThat(header[154]).isZero();
        assertThat(header[155]).isEqualTo((byte) ' ');
    }

    @Test
    void endsWithTwoZeroBlocks() throws IOException {
        byte[] tar = tar("a.class");

        assertThat(tar).hasSize(4 * BLOCK);
        assertThat(Arrays.copyOfRange(tar, 2 * BLOCK, 4 * BLOCK)).containsOnly(0);
    }

    private static byte[] firstHeader(String path) throws IOException {
        return Arrays.copyOf(tar(path), BLOCK);
    }

    private static byte[] tar(String path) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        TarWriter tar = new TarWriter(out);
        tar.file(path, "abc".getBytes(StandardCharsets.US_ASCII));
        tar.finish();
        return out.toByteArray();
    }

    private static String field(byte[] header, int offset, int length) {
        int end = offset;
        while (end < offset + length && header[end] != 0) {
            end++;
        }
        return new String(header, offset, end - offset, StandardCharsets.UTF_8);
    }
}
