#ifndef SHARE_OOPS_RESOLVEDINVOKEDYNAMICINFO_HPP
#define SHARE_OOPS_RESOLVEDINVOKEDYNAMICINFO_HPP

#include "memory/metaspaceClosure.hpp"

class Method;
class ResolvedIndyInfo : public MetaspaceObj {
     Method* _method;
     u2 _resolved_references_index;
     u2 _cpool_index;
     u2 _number_of_parameters;
     u1 _return_type;
     bool _has_appendix;

public:
    ResolvedIndyInfo() :
        _method(nullptr),
        _resolved_references_index(0),
        _cpool_index(0),
        _number_of_parameters(0),
        _return_type(0),
        _has_appendix(false) {}
    ResolvedIndyInfo(u2 resolved_references_index, u2 cpool_index) :
        _method(nullptr),
        _resolved_references_index(resolved_references_index),
        _cpool_index(cpool_index),
        _number_of_parameters(0),
        _return_type(0),
        _has_appendix(false) {}

    // Getters
    Method* method() const               { return _method;                    }
    u2 resolved_references_index() const { return _resolved_references_index; }
    u2 cpool_index() const               { return _cpool_index;               }
    u2 num_parameters() const            { return _number_of_parameters;      }
    u1 return_type() const               { return _return_type;               }
    bool has_appendix() const            { return _has_appendix;              }
    bool has_local_signature() const     { return true;                       }
    bool is_final() const                { return true;                       }
    bool is_resolved() const             { return _method != nullptr;         }

    // Printing
    void print_on(outputStream* st) const;

    // Initialize with fields available before resolution
    void init(u2 resolved_references_index, u2 cpool_index) {
        _resolved_references_index = resolved_references_index;
        _cpool_index = cpool_index;
    }

    // Fill remaining fields
    void fill_in(Method* m, u2 num_params, u1 return_type, bool has_appendix) {
        _number_of_parameters = num_params; // might be parameter size()
        _return_type = return_type;
        _has_appendix = has_appendix;
        //_method = m;
        Atomic::release_store(&_method, m);
    }
    void metaspace_pointers_do(MetaspaceClosure* it);

    // Offsets
    static ByteSize method_offset()                    { return byte_offset_of(ResolvedIndyInfo, _method);                    }
    static ByteSize resolved_references_index_offset() { return byte_offset_of(ResolvedIndyInfo, _resolved_references_index); }
    static ByteSize result_type_offset()               { return byte_offset_of(ResolvedIndyInfo, _return_type);               }
    static ByteSize has_appendix_offset()              { return byte_offset_of(ResolvedIndyInfo, _has_appendix);              }
    static ByteSize num_parameters_offset()            { return byte_offset_of(ResolvedIndyInfo, _number_of_parameters);      }
};

#endif // SHARE_OOPS_RESOLVEDINVOKEDYNAMICINFO_HPP
