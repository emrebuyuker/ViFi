package com.salticusteam.vifi;

import com.salticusteam.vifi.domain.Department;
import com.salticusteam.vifi.domain.Faculty;
import com.salticusteam.vifi.domain.University;

import java.util.ArrayList;
import java.util.List;

public class Universities {
    private static List<University> universities = new ArrayList<>();
    static {

        Department digerDepartment = new Department();
        digerDepartment.setName("DİGER");

        Faculty digerFaculty = new Faculty();
        digerFaculty.setName("DİGER");
        digerFaculty.getDepartments().add(digerDepartment);

        Department electronicDepartment = new Department();
        electronicDepartment.setName("ELEKTRİK ELEKTRONİK MÜHENDİSLİĞİ");

        Faculty engineeringFaculty = new Faculty();
        engineeringFaculty.setName("MÜHENDİSLİK FAKÜLTESİ");
        engineeringFaculty.getDepartments().add(electronicDepartment);
        engineeringFaculty.getDepartments().add(digerDepartment);

        Department mathematicsDepartment = new Department();
        mathematicsDepartment.setName("MATEMATİK BÖLÜMÜ");

        Faculty fenEdebiyatFaculty = new Faculty();
        fenEdebiyatFaculty.setName("FEN EDEBİYAT FAKÜLTESİ");
        fenEdebiyatFaculty.getDepartments().add(mathematicsDepartment);
        fenEdebiyatFaculty.getDepartments().add(digerDepartment);

        Department compoterDepartment = new Department();
        compoterDepartment.setName("BİLGİSAYAR BÖLÜMÜ");

        Faculty compoterFaculty = new Faculty();
        compoterFaculty.setName("BİLGİSAYAR VE BİLİŞİM BİLİMLERİ FAKÜLTESİ");
        compoterFaculty.getDepartments().add(compoterDepartment);
        compoterFaculty.getDepartments().add(digerDepartment);


        University sakaryaUni = new University();
        sakaryaUni.setName("SAKARYA ÜNİVERSİTESİ");
        sakaryaUni.getFaculties().add(engineeringFaculty);
        sakaryaUni.getFaculties().add(fenEdebiyatFaculty);
        sakaryaUni.getFaculties().add(compoterFaculty);
        sakaryaUni.getFaculties().add(digerFaculty);


        University canakkaleUni = new University();
        canakkaleUni.setName("ÇANAKKALE ONSEKİZ MART ÜNİVERSİTESİ");
        canakkaleUni.getFaculties().add(fenEdebiyatFaculty);
        canakkaleUni.getFaculties().add(digerFaculty);

        University bulentEcevitUni = new University();
        bulentEcevitUni.setName("BÜLENT ECEVİT ÜNİVERSİTESİ");
        bulentEcevitUni.getFaculties().add(fenEdebiyatFaculty);
        bulentEcevitUni.getFaculties().add(digerFaculty);

        University digerUni = new University();
        digerUni.setName("DİGER");
        digerUni.getFaculties().add(digerFaculty);

        universities.add(sakaryaUni);
        universities.add(canakkaleUni);
        universities.add(bulentEcevitUni);
        universities.add(digerUni);
    }

    public static List<University> getUniversities() {
        return universities;
    }
}
