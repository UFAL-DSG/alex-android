package cz.cuni.mff.ufal.androidalex;


/**
 * From:  com.sibilantsolutions.grison.sound.adpcm
 */
public class ADPCMEncoder
{

    private int index = 0;
    private int predictedSample = 0;

    public byte[] encode( byte[] pcm )
    {
        return encode( pcm, 0, pcm.length );
    }

    public byte[] encode( byte[] pcm, int offset, int len )
    {
        return encodeFos( pcm, offset, len );
    }

    //This is ported from Foscam's ipcam_sample/ipcam.cpp
    private byte[] encodeFos( byte[] pcm, int offset, int len )
    {
        byte[] ret = new byte[len / 4];

        for ( int i = 0; i < ( len / 2 ); i++ )
        {
            //int curSample = pcm[i] & 0xFF;
            int curSample = pcm[i];

            int delta = curSample - predictedSample;

            boolean isSignBit = false;

            if ( delta < 0 )
            {
                delta = -delta;
                isSignBit = true;
            }

            int code = 4 * delta / ADPCMDecoder.step_table[index];

            if ( code > 7 )
                code = 7;

            delta = ( ADPCMDecoder.step_table[index] * code ) / 4 +
                    ADPCMDecoder.step_table[index] / 8;

            if ( isSignBit )
                delta = -delta;

            predictedSample += delta;

            if ( predictedSample > 32767 )
                predictedSample = 32767;
            else if ( predictedSample < -32768 )
                predictedSample = -32768;

            index += ADPCMDecoder.index_adjust[code];

            if ( index < 0 )
                index = 0;
            else if ( index > 88 )
                index = 88;

            if ( i % 2 != 0 )
                ret[i >> 1] |= code | ( isSignBit ? 8 : 0 );
            else
                ret[i >> 1] = (byte)( ( code | ( isSignBit ? 8 : 0 ) ) << 4 );

        }

        return ret;
    }

}