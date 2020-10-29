package org.openlca.proto.output;

import java.time.Instant;
import java.util.Arrays;

import org.openlca.core.model.UnitGroup;
import org.openlca.core.model.Version;
import org.openlca.proto.Proto;
import org.openlca.util.Strings;

public class UnitGroupWriter {

  private final WriterConfig config;

  public UnitGroupWriter(WriterConfig config) {
    this.config = config;
  }

  public Proto.UnitGroup write(UnitGroup group) {
    var proto = Proto.UnitGroup.newBuilder();
    if (group == null)
      return proto.build();

    // root entitiy fields
    proto.setId(Strings.orEmpty(group.refId));
    proto.setName(Strings.orEmpty(group.name));
    proto.setDescription(Strings.orEmpty(group.description));
    proto.setVersion(Version.asString(group.version));
    if (group.lastChange != 0L) {
      var instant = Instant.ofEpochMilli(group.lastChange);
      proto.setLastChange(instant.toString());
    }

    // categorized entity fields
    if (Strings.notEmpty(group.tags)) {
      Arrays.stream(group.tags.split(","))
        .filter(Strings::notEmpty)
        .forEach(proto::addTags);
    }
    if (group.category != null) {
      proto.setCategory(Refs.toRef(group.category, config));
    }

    // model specific fields
    // TODO

    return proto.build();
  }
}