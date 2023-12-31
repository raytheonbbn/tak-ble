/*
 *
 * TAK-BLE
 * Copyright (c) 2023 Raytheon Technologies
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see https://www.gnu.org/licenses/.
 * https://github.com/atapas/add-copyright.git
 *
 */

// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: takcontrol.proto

package com.atakmap.android.ble_forwarder.proto.generated;

public final class Takcontrol {
  private Takcontrol() {}
  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistryLite registry) {
  }

  public static void registerAllExtensions(
      com.google.protobuf.ExtensionRegistry registry) {
    registerAllExtensions(
        (com.google.protobuf.ExtensionRegistryLite) registry);
  }
  public interface TakControlOrBuilder extends
      // @@protoc_insertion_point(interface_extends:TakControl)
      com.google.protobuf.MessageOrBuilder {

    /**
     * <pre>
     * Lowest TAK protocol version supported
     * If not filled in (reads as 0), version 1 is assumed
     * </pre>
     *
     * <code>optional uint32 minProtoVersion = 1;</code>
     */
    int getMinProtoVersion();

    /**
     * <pre>
     * Highest TAK protocol version supported
     * If not filled in (reads as 0), version 1 is assumed
     * </pre>
     *
     * <code>optional uint32 maxProtoVersion = 2;</code>
     */
    int getMaxProtoVersion();
  }
  /**
   * <pre>
   * TAK Protocol control message
   * This specifies to a recipient what versions
   * of protocol elements this sender supports during
   * decoding.
   * </pre>
   *
   * Protobuf type {@code TakControl}
   */
  public  static final class TakControl extends
      com.google.protobuf.GeneratedMessageV3 implements
      // @@protoc_insertion_point(message_implements:TakControl)
      TakControlOrBuilder {
    // Use TakControl.newBuilder() to construct.
    private TakControl(com.google.protobuf.GeneratedMessageV3.Builder<?> builder) {
      super(builder);
    }
    private TakControl() {
      minProtoVersion_ = 0;
      maxProtoVersion_ = 0;
    }

    @Override
    public final com.google.protobuf.UnknownFieldSet
    getUnknownFields() {
      return com.google.protobuf.UnknownFieldSet.getDefaultInstance();
    }
    private TakControl(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      this();
      int mutable_bitField0_ = 0;
      try {
        boolean done = false;
        while (!done) {
          int tag = input.readTag();
          switch (tag) {
            case 0:
              done = true;
              break;
            default: {
              if (!input.skipField(tag)) {
                done = true;
              }
              break;
            }
            case 8: {

              minProtoVersion_ = input.readUInt32();
              break;
            }
            case 16: {

              maxProtoVersion_ = input.readUInt32();
              break;
            }
          }
        }
      } catch (com.google.protobuf.InvalidProtocolBufferException e) {
        throw e.setUnfinishedMessage(this);
      } catch (java.io.IOException e) {
        throw new com.google.protobuf.InvalidProtocolBufferException(
            e).setUnfinishedMessage(this);
      } finally {
        makeExtensionsImmutable();
      }
    }
    public static final com.google.protobuf.Descriptors.Descriptor
        getDescriptor() {
      return Takcontrol.internal_static_TakControl_descriptor;
    }

    protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
        internalGetFieldAccessorTable() {
      return Takcontrol.internal_static_TakControl_fieldAccessorTable
          .ensureFieldAccessorsInitialized(
              Takcontrol.TakControl.class, Takcontrol.TakControl.Builder.class);
    }

    public static final int MINPROTOVERSION_FIELD_NUMBER = 1;
    private int minProtoVersion_;
    /**
     * <pre>
     * Lowest TAK protocol version supported
     * If not filled in (reads as 0), version 1 is assumed
     * </pre>
     *
     * <code>optional uint32 minProtoVersion = 1;</code>
     */
    public int getMinProtoVersion() {
      return minProtoVersion_;
    }

    public static final int MAXPROTOVERSION_FIELD_NUMBER = 2;
    private int maxProtoVersion_;
    /**
     * <pre>
     * Highest TAK protocol version supported
     * If not filled in (reads as 0), version 1 is assumed
     * </pre>
     *
     * <code>optional uint32 maxProtoVersion = 2;</code>
     */
    public int getMaxProtoVersion() {
      return maxProtoVersion_;
    }

    private byte memoizedIsInitialized = -1;
    public final boolean isInitialized() {
      byte isInitialized = memoizedIsInitialized;
      if (isInitialized == 1) return true;
      if (isInitialized == 0) return false;

      memoizedIsInitialized = 1;
      return true;
    }

    public void writeTo(com.google.protobuf.CodedOutputStream output)
                        throws java.io.IOException {
      if (minProtoVersion_ != 0) {
        output.writeUInt32(1, minProtoVersion_);
      }
      if (maxProtoVersion_ != 0) {
        output.writeUInt32(2, maxProtoVersion_);
      }
    }

    public int getSerializedSize() {
      int size = memoizedSize;
      if (size != -1) return size;

      size = 0;
      if (minProtoVersion_ != 0) {
        size += com.google.protobuf.CodedOutputStream
          .computeUInt32Size(1, minProtoVersion_);
      }
      if (maxProtoVersion_ != 0) {
        size += com.google.protobuf.CodedOutputStream
          .computeUInt32Size(2, maxProtoVersion_);
      }
      memoizedSize = size;
      return size;
    }

    private static final long serialVersionUID = 0L;
    @Override
    public boolean equals(final Object obj) {
      if (obj == this) {
       return true;
      }
      if (!(obj instanceof Takcontrol.TakControl)) {
        return super.equals(obj);
      }
      Takcontrol.TakControl other = (Takcontrol.TakControl) obj;

      boolean result = true;
      result = result && (getMinProtoVersion()
          == other.getMinProtoVersion());
      result = result && (getMaxProtoVersion()
          == other.getMaxProtoVersion());
      return result;
    }

    @Override
    public int hashCode() {
      if (memoizedHashCode != 0) {
        return memoizedHashCode;
      }
      int hash = 41;
      hash = (19 * hash) + getDescriptorForType().hashCode();
      hash = (37 * hash) + MINPROTOVERSION_FIELD_NUMBER;
      hash = (53 * hash) + getMinProtoVersion();
      hash = (37 * hash) + MAXPROTOVERSION_FIELD_NUMBER;
      hash = (53 * hash) + getMaxProtoVersion();
      hash = (29 * hash) + unknownFields.hashCode();
      memoizedHashCode = hash;
      return hash;
    }

    public static Takcontrol.TakControl parseFrom(
        com.google.protobuf.ByteString data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static Takcontrol.TakControl parseFrom(
        com.google.protobuf.ByteString data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static Takcontrol.TakControl parseFrom(byte[] data)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data);
    }
    public static Takcontrol.TakControl parseFrom(
        byte[] data,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws com.google.protobuf.InvalidProtocolBufferException {
      return PARSER.parseFrom(data, extensionRegistry);
    }
    public static Takcontrol.TakControl parseFrom(java.io.InputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input);
    }
    public static Takcontrol.TakControl parseFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input, extensionRegistry);
    }
    public static Takcontrol.TakControl parseDelimitedFrom(java.io.InputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseDelimitedWithIOException(PARSER, input);
    }
    public static Takcontrol.TakControl parseDelimitedFrom(
        java.io.InputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseDelimitedWithIOException(PARSER, input, extensionRegistry);
    }
    public static Takcontrol.TakControl parseFrom(
        com.google.protobuf.CodedInputStream input)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input);
    }
    public static Takcontrol.TakControl parseFrom(
        com.google.protobuf.CodedInputStream input,
        com.google.protobuf.ExtensionRegistryLite extensionRegistry)
        throws java.io.IOException {
      return com.google.protobuf.GeneratedMessageV3
          .parseWithIOException(PARSER, input, extensionRegistry);
    }

    public Builder newBuilderForType() { return newBuilder(); }
    public static Builder newBuilder() {
      return DEFAULT_INSTANCE.toBuilder();
    }
    public static Builder newBuilder(Takcontrol.TakControl prototype) {
      return DEFAULT_INSTANCE.toBuilder().mergeFrom(prototype);
    }
    public Builder toBuilder() {
      return this == DEFAULT_INSTANCE
          ? new Builder() : new Builder().mergeFrom(this);
    }

    @Override
    protected Builder newBuilderForType(
        com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
      Builder builder = new Builder(parent);
      return builder;
    }
    /**
     * <pre>
     * TAK Protocol control message
     * This specifies to a recipient what versions
     * of protocol elements this sender supports during
     * decoding.
     * </pre>
     *
     * Protobuf type {@code TakControl}
     */
    public static final class Builder extends
        com.google.protobuf.GeneratedMessageV3.Builder<Builder> implements
        // @@protoc_insertion_point(builder_implements:TakControl)
        Takcontrol.TakControlOrBuilder {
      public static final com.google.protobuf.Descriptors.Descriptor
          getDescriptor() {
        return Takcontrol.internal_static_TakControl_descriptor;
      }

      protected com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
          internalGetFieldAccessorTable() {
        return Takcontrol.internal_static_TakControl_fieldAccessorTable
            .ensureFieldAccessorsInitialized(
                Takcontrol.TakControl.class, Takcontrol.TakControl.Builder.class);
      }

      // Construct using Takcontrol.TakControl.newBuilder()
      private Builder() {
        maybeForceBuilderInitialization();
      }

      private Builder(
          com.google.protobuf.GeneratedMessageV3.BuilderParent parent) {
        super(parent);
        maybeForceBuilderInitialization();
      }
      private void maybeForceBuilderInitialization() {
        if (com.google.protobuf.GeneratedMessageV3
                .alwaysUseFieldBuilders) {
        }
      }
      public Builder clear() {
        super.clear();
        minProtoVersion_ = 0;

        maxProtoVersion_ = 0;

        return this;
      }

      public com.google.protobuf.Descriptors.Descriptor
          getDescriptorForType() {
        return Takcontrol.internal_static_TakControl_descriptor;
      }

      public Takcontrol.TakControl getDefaultInstanceForType() {
        return Takcontrol.TakControl.getDefaultInstance();
      }

      public Takcontrol.TakControl build() {
        Takcontrol.TakControl result = buildPartial();
        if (!result.isInitialized()) {
          throw newUninitializedMessageException(result);
        }
        return result;
      }

      public Takcontrol.TakControl buildPartial() {
        Takcontrol.TakControl result = new Takcontrol.TakControl(this);
        result.minProtoVersion_ = minProtoVersion_;
        result.maxProtoVersion_ = maxProtoVersion_;
        onBuilt();
        return result;
      }

      public Builder clone() {
        return (Builder) super.clone();
      }
      public Builder setField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          Object value) {
        return (Builder) super.setField(field, value);
      }
      public Builder clearField(
          com.google.protobuf.Descriptors.FieldDescriptor field) {
        return (Builder) super.clearField(field);
      }
      public Builder clearOneof(
          com.google.protobuf.Descriptors.OneofDescriptor oneof) {
        return (Builder) super.clearOneof(oneof);
      }
      public Builder setRepeatedField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          int index, Object value) {
        return (Builder) super.setRepeatedField(field, index, value);
      }
      public Builder addRepeatedField(
          com.google.protobuf.Descriptors.FieldDescriptor field,
          Object value) {
        return (Builder) super.addRepeatedField(field, value);
      }
      public Builder mergeFrom(com.google.protobuf.Message other) {
        if (other instanceof Takcontrol.TakControl) {
          return mergeFrom((Takcontrol.TakControl)other);
        } else {
          super.mergeFrom(other);
          return this;
        }
      }

      public Builder mergeFrom(Takcontrol.TakControl other) {
        if (other == Takcontrol.TakControl.getDefaultInstance()) return this;
        if (other.getMinProtoVersion() != 0) {
          setMinProtoVersion(other.getMinProtoVersion());
        }
        if (other.getMaxProtoVersion() != 0) {
          setMaxProtoVersion(other.getMaxProtoVersion());
        }
        onChanged();
        return this;
      }

      public final boolean isInitialized() {
        return true;
      }

      public Builder mergeFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws java.io.IOException {
        Takcontrol.TakControl parsedMessage = null;
        try {
          parsedMessage = PARSER.parsePartialFrom(input, extensionRegistry);
        } catch (com.google.protobuf.InvalidProtocolBufferException e) {
          parsedMessage = (Takcontrol.TakControl) e.getUnfinishedMessage();
          throw e.unwrapIOException();
        } finally {
          if (parsedMessage != null) {
            mergeFrom(parsedMessage);
          }
        }
        return this;
      }

      private int minProtoVersion_ ;
      /**
       * <pre>
       * Lowest TAK protocol version supported
       * If not filled in (reads as 0), version 1 is assumed
       * </pre>
       *
       * <code>optional uint32 minProtoVersion = 1;</code>
       */
      public int getMinProtoVersion() {
        return minProtoVersion_;
      }
      /**
       * <pre>
       * Lowest TAK protocol version supported
       * If not filled in (reads as 0), version 1 is assumed
       * </pre>
       *
       * <code>optional uint32 minProtoVersion = 1;</code>
       */
      public Builder setMinProtoVersion(int value) {
        
        minProtoVersion_ = value;
        onChanged();
        return this;
      }
      /**
       * <pre>
       * Lowest TAK protocol version supported
       * If not filled in (reads as 0), version 1 is assumed
       * </pre>
       *
       * <code>optional uint32 minProtoVersion = 1;</code>
       */
      public Builder clearMinProtoVersion() {
        
        minProtoVersion_ = 0;
        onChanged();
        return this;
      }

      private int maxProtoVersion_ ;
      /**
       * <pre>
       * Highest TAK protocol version supported
       * If not filled in (reads as 0), version 1 is assumed
       * </pre>
       *
       * <code>optional uint32 maxProtoVersion = 2;</code>
       */
      public int getMaxProtoVersion() {
        return maxProtoVersion_;
      }
      /**
       * <pre>
       * Highest TAK protocol version supported
       * If not filled in (reads as 0), version 1 is assumed
       * </pre>
       *
       * <code>optional uint32 maxProtoVersion = 2;</code>
       */
      public Builder setMaxProtoVersion(int value) {
        
        maxProtoVersion_ = value;
        onChanged();
        return this;
      }
      /**
       * <pre>
       * Highest TAK protocol version supported
       * If not filled in (reads as 0), version 1 is assumed
       * </pre>
       *
       * <code>optional uint32 maxProtoVersion = 2;</code>
       */
      public Builder clearMaxProtoVersion() {
        
        maxProtoVersion_ = 0;
        onChanged();
        return this;
      }
      public final Builder setUnknownFields(
          final com.google.protobuf.UnknownFieldSet unknownFields) {
        return this;
      }

      public final Builder mergeUnknownFields(
          final com.google.protobuf.UnknownFieldSet unknownFields) {
        return this;
      }


      // @@protoc_insertion_point(builder_scope:TakControl)
    }

    // @@protoc_insertion_point(class_scope:TakControl)
    private static final Takcontrol.TakControl DEFAULT_INSTANCE;
    static {
      DEFAULT_INSTANCE = new Takcontrol.TakControl();
    }

    public static Takcontrol.TakControl getDefaultInstance() {
      return DEFAULT_INSTANCE;
    }

    private static final com.google.protobuf.Parser<TakControl>
        PARSER = new com.google.protobuf.AbstractParser<TakControl>() {
      public TakControl parsePartialFrom(
          com.google.protobuf.CodedInputStream input,
          com.google.protobuf.ExtensionRegistryLite extensionRegistry)
          throws com.google.protobuf.InvalidProtocolBufferException {
          return new TakControl(input, extensionRegistry);
      }
    };

    public static com.google.protobuf.Parser<TakControl> parser() {
      return PARSER;
    }

    @Override
    public com.google.protobuf.Parser<TakControl> getParserForType() {
      return PARSER;
    }

    public Takcontrol.TakControl getDefaultInstanceForType() {
      return DEFAULT_INSTANCE;
    }

  }

  private static final com.google.protobuf.Descriptors.Descriptor
    internal_static_TakControl_descriptor;
  private static final 
    com.google.protobuf.GeneratedMessageV3.FieldAccessorTable
      internal_static_TakControl_fieldAccessorTable;

  public static com.google.protobuf.Descriptors.FileDescriptor
      getDescriptor() {
    return descriptor;
  }
  private static  com.google.protobuf.Descriptors.FileDescriptor
      descriptor;
  static {
    String[] descriptorData = {
      "\n\020takcontrol.proto\022\037atakmap.commoncommo." +
      "protobuf.v1\">\n\nTakControl\022\027\n\017minProtoVer" +
      "sion\030\001 \001(\r\022\027\n\017maxProtoVersion\030\002 \001(\rb\006pro" +
      "to3"
    };
    com.google.protobuf.Descriptors.FileDescriptor.InternalDescriptorAssigner assigner =
        new com.google.protobuf.Descriptors.FileDescriptor.    InternalDescriptorAssigner() {
          public com.google.protobuf.ExtensionRegistry assignDescriptors(
              com.google.protobuf.Descriptors.FileDescriptor root) {
            descriptor = root;
            return null;
          }
        };
    com.google.protobuf.Descriptors.FileDescriptor
      .internalBuildGeneratedFileFrom(descriptorData,
        new com.google.protobuf.Descriptors.FileDescriptor[] {
        }, assigner);
    internal_static_TakControl_descriptor =
      getDescriptor().getMessageTypes().get(0);
    internal_static_TakControl_fieldAccessorTable = new
      com.google.protobuf.GeneratedMessageV3.FieldAccessorTable(
        internal_static_TakControl_descriptor,
        new String[] { "MinProtoVersion", "MaxProtoVersion", });
  }

  // @@protoc_insertion_point(outer_class_scope)
}
