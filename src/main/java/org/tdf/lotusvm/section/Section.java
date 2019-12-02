package org.tdf.lotusvm.section;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Each section consists of
 * • a one-byte section id,
 * • the u32 size of the contents, in bytes,
 * • the actual contents, whose structure is depended on the section id.
 *
 *
 */
@AllArgsConstructor
public abstract class Section {
   @Getter
   private SectionID id;
   @Getter
   private long size; // unsigned integer
   @Getter(AccessLevel.PROTECTED)
   private byte[] payload;


   abstract void readPayload();

   // clean payload after read
   public void clearPayload(){
       payload = null;
   }
}
