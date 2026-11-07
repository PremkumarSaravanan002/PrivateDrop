package com.privatedrop.service;


import com.privatedrop.entity.Note;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.privatedrop.repository.NoteRepository;

@Service
public class EchoService {
    private final NoteRepository noteRepository;
    public EchoService(NoteRepository noteRepository) {
        this.noteRepository = noteRepository;
    }
    public String echo(String message) {
        return message;
    }

    @Transactional

    public Note saveNote(String content) {
        Note note = new Note(content);
        return noteRepository.save(note);
    }
}
